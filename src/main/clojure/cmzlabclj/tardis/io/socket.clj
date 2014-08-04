;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013 Cherimoia, LLC. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  cmzlabclj.tardis.io.socket

  (:require [clojure.tools.logging :as log :only (info warn error debug)]
            [clojure.string :as cstr])

  (:use [cmzlabclj.tardis.io.core]
        [cmzlabclj.tardis.core.sys]
        [cmzlabclj.nucleus.util.process :only [Coroutine] ]
        [cmzlabclj.nucleus.util.meta :only [GetCldr] ]
        [cmzlabclj.nucleus.util.core :only [test-posnum ConvLong spos?] ]
        [cmzlabclj.nucleus.util.str :only [strim nsb hgl?] ]
        [cmzlabclj.nucleus.util.seqnum :only [NextLong] ])

  (:import  [java.net InetAddress ServerSocket Socket]
            [org.apache.commons.io IOUtils]
            [com.zotohlab.frwk.core Identifiable]
            [com.zotohlab.gallifrey.io SocketEvent]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn MakeSocketIO ""

  [container]

  (MakeEmitter container :czc.tardis.io/SocketIO))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.tardis.io/SocketIO

  [co & args]

  (let [^Socket soc (first args)
        eeid (NextLong) ]
    (with-meta
      (reify

        Identifiable
        (id [_] eeid)

        SocketEvent
        (bindSession [_ s] nil)
        (getSession [_] nil)
        (getId [_] eeid)
        (checkAuthenticity [_] false)
        (getSockOut [_] (.getOutputStream soc))
        (getSockIn [_] (.getInputStream soc))
        (emitter [_] co)
        (dispose [_] (IOUtils/closeQuietly soc)))

      { :typeid :czc.tardis.io/SocketEvent }
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.tardis.io/SocketIO

  [^cmzlabclj.tardis.core.sys.Element co cfg]

  (test-posnum "socket-io port" (:port cfg))
  (let [tout (:timeoutMillis cfg)
        blog (:backlog cfg) ]
    (with-local-vars [cpy (transient cfg)]
      (var-set cpy (assoc! @cpy :backlog
                           (if (spos? blog) blog 100)))
      (var-set cpy (assoc! @cpy
                           :host (strim (:host cfg))))
      (var-set cpy (assoc! @cpy
                           :timeoutMillis
                           (if (spos? tout) tout 0)))
      (.setAttr! co :emcfg (persistent! @cpy)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompInitialize :czc.tardis.io/SocketIO

  [^cmzlabclj.tardis.core.sys.Element co]

  (let [cfg (.getAttr co :emcfg)
        backlog (:backlog cfg)
        host (:host cfg)
        port (:port cfg)
        ip (if (hgl? host)
             (InetAddress/getByName host)
             (InetAddress/getLocalHost))
        soc (ServerSocket. port backlog ip) ]
    (log/info "opened Server Socket " soc  " (bound?) " (.isBound soc))
    (doto soc (.setReuseAddress true))
    (.setAttr! co :ssocket soc)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- sockItDown ""

  [^cmzlabclj.tardis.io.core.EmitterAPI co ^Socket soc]

  (.dispatch co (IOESReifyEvent co soc) {} ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.tardis.io/SocketIO

  [^cmzlabclj.tardis.core.sys.Element co]

  (let [^ServerSocket ssoc (.getAttr co :ssocket)
        cl (GetCldr) ]
    (when-not (nil? ssoc)
      (Coroutine #(while (.isBound ssoc)
                    (try
                      (sockItDown co (.accept ssoc))
                      (catch Throwable e#
                        (log/warn e# "")
                        (IOUtils/closeQuietly ssoc)
                        (.setAttr! co :ssocket nil))))
                 cl))
    (IOESStarted co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.tardis.io/SocketIO

  [^cmzlabclj.tardis.core.sys.Element co]

  (let [^ServerSocket ssoc (.getAttr co :ssocket) ]
    (IOUtils/closeQuietly ssoc)
    (.setAttr! co :ssocket nil)
    (IOESStopped co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private socket-eof nil)

