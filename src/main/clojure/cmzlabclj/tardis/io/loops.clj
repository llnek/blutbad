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

  cmzlabclj.tardis.io.loops

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])

  (:use [cmzlabclj.nucleus.util.core :only [ternary spos? MubleAPI TryC] ]
        [cmzlabclj.nucleus.util.process :only [Coroutine SafeWait] ]
        [cmzlabclj.nucleus.util.dates :only [ParseDate] ]
        [cmzlabclj.nucleus.util.meta :only [GetCldr] ]
        [cmzlabclj.nucleus.util.seqnum :only [NextLong] ]
        [cmzlabclj.nucleus.util.str :only [nsb hgl? strim] ]
        [cmzlabclj.tardis.core.sys]
        [cmzlabclj.tardis.io.core])

  (:import  [java.util Date Timer TimerTask]
            [com.zotohlab.gallifrey.io TimerEvent]
            [com.zotohlab.frwk.core Identifiable Startable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti LoopableWakeup "" (fn [a & args] (:typeid (meta a)) ))
(defmulti LoopableSchedule "" (fn [a] (:typeid (meta a)) ))
(defmulti LoopableOneLoop "" (fn [a] (:typeid (meta a)) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- config-repeat-timer ""

  [^Timer tm delays intv func]

  (let [tt (proxy [TimerTask][]
              (run []
                (TryC (when (fn? func) (func)))))
        [^Date dw ^long ds] delays ]
    (when (instance? Date dw)
      (.schedule tm tt dw (long intv)))
    (when (number? ds)
      (.schedule tm tt ds (long intv)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- config-timer ""

  [^Timer tm delays func]

  (let [tt (proxy [TimerTask][]
              (run []
                (when (fn? func) (func))))
        [^Date dw ^long ds] delays]
    (when (instance? Date dw)
      (.schedule tm tt dw) )
    (when (number? ds)
      (.schedule tm tt ds))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- config-timertask ""

  [^cmzlabclj.tardis.core.sys.Element co]

  (let [cfg (.getAttr co :emcfg)
        intv (:intervalMillis cfg)
        t (:timer cfg)
        ds (:delayMillis cfg)
        dw (:delayWhen cfg)
        func #(LoopableWakeup co) ]
    (if (number? intv)
      (config-repeat-timer t [dw ds] intv func)
      (config-timer t [dw ds] func))
    co
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CfgLoopable ""

  [^cmzlabclj.tardis.core.sys.Element co cfg]

  (let [intv (:intervalSecs cfg)
        ds (:delaySecs cfg)
        dw (:delayWhen cfg) ]
    (with-local-vars [cpy (transient cfg)]
      (if (instance? Date dw)
        (var-set cpy (assoc! :delayWhen dw))
        (var-set cpy (assoc! :delayMillis (* 1000 (if (spos? ds) ds 3)))))
      (when (spos? intv)
        (var-set cpy (assoc! :intervalMillis (* 1000 intv))))
      (-> (persistent! @cpy)
          (dissoc :delaySecs)
          (dissoc :intervalSecs)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- start-timer ""

  [^cmzlabclj.tardis.core.sys.Element co]

  (.setAttr! co :timer (Timer. true))
  (LoopableSchedule co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- kill-timer ""

  [^cmzlabclj.tardis.core.sys.Element co]

  (let [^Timer t (.getAttr co :timer) ]
    (TryC
        (when-not (nil? t) (.cancel t)) )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Repeating Timer
(defn MakeRepeatingTimer ""

  [container]

  (MakeEmitter container :czc.tardis.io/RepeatingTimer))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.tardis.io/RepeatingTimer

  [co & args]

  (let [eeid (NextLong) ]
    (with-meta
      (reify

        Identifiable
        (id [_] eeid)

        TimerEvent
        (bindSession [_ s] nil)
        (getSession [_] nil)
        (getId [_] eeid)
        (checkAuthenticity [_] false)
        (emitter [_] co)
        (isRepeating [_] true))

      { :typeid :czc.tardis.io/TimerEvent }
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.tardis.io/RepeatingTimer

  [^cmzlabclj.tardis.core.sys.Element co cfg]

  (let [c2 (CfgLoopable co cfg)]
    (.setAttr! co :emcfg c2)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.tardis.io/RepeatingTimer

  [co]

  (start-timer co)
  (IOESStarted co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.tardis.io/RepeatingTimer

  [co]

  (kill-timer co)
  (IOESStopped co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableWakeup :czc.tardis.io/RepeatingTimer

  [^cmzlabclj.tardis.io.core.EmitterAPI co & args]

  (.dispatch co (IOESReifyEvent co) {} ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableSchedule :default

  [co]

  (config-timertask co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Once Timer
(defn MakeOnceTimer ""

  [container]

  (MakeEmitter container :czc.tardis.io/OnceTimer))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESReifyEvent :czc.tardis.io/OnceTimer

  [co & args]

  (let [eeid (NextLong) ]
    (with-meta
      (reify

        Identifiable
        (id [_] eeid)

        TimerEvent
        (bindSession [_ s] nil)
        (getSession [_] nil)
        (getId [_] eeid)
        (checkAuthenticity [_] false)
        (emitter [_] co)
        (isRepeating [_] false))

      { :typeid :czc.tardis.io/TimerEvent }
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod CompConfigure :czc.tardis.io/OnceTimer

  [^cmzlabclj.tardis.core.sys.Element co cfg]

  ;; get rid of interval millis field, if any
  (let [c2 (CfgLoopable co cfg) ]
    (.setAttr! co :emcfg (dissoc c2 :intervalMillis))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.tardis.io/OnceTimer

  [co]

  (start-timer co)
  (IOESStarted co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.tardis.io/OnceTimer

  [co]

  (kill-timer co)
  (IOESStopped co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableWakeup :czc.tardis.io/OnceTimer

  [^cmzlabclj.tardis.io.core.EmitterAPI co & args]

  (.dispatch co (IOESReifyEvent co) {} )
  (.stop ^Startable co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Threaded Timer

;;(defmethod loopable-oneloop :default [co] nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableSchedule :czc.tardis.io/ThreadedTimer

  [^cmzlabclj.tardis.core.sys.Element co]

  (let [cfg (.getAttr co :emcfg)
        intv (:intervalMillis cfg)
        loopy (atom true)
        cl (GetCldr) ]
    (log/info "threaded one timer - interval = " intv)
    (.setAttr! co :loopy loopy)
    (Coroutine #(while @loopy (LoopableWakeup co intv)) cl)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod LoopableWakeup :czc.tardis.io/ThreadedTimer

  [co & args]

  (TryC (LoopableOneLoop co) )
  (SafeWait (first args) ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStart :czc.tardis.io/ThreadedTimer

  [^cmzlabclj.tardis.core.sys.Element co]

  (let [cfg (.getAttr co :emcfg)
        intv (:intervalMillis cfg)
        ds (:delayMillis cfg)
        dw (:delayWhen cfg)
        loopy (atom true)
        cl (GetCldr)
        func #(LoopableSchedule co) ]
    (.setAttr! co :loopy loopy)
    (if (or (number? ds) (instance? Date dw))
      (config-timer (Timer.) [dw ds] func)
      (func))
    (IOESStarted co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod IOESStop :czc.tardis.io/ThreadedTimer

  [^cmzlabclj.tardis.core.sys.Element co]

  (let [loopy (.getAttr co :loopy) ]
    (reset! loopy false)
    (IOESStopped co)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private loops-eof nil)

