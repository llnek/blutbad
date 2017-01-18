;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc "Implementation for email services."
      :author "Kenneth Leung"}

  czlab.wabbit.io.mails

  (:require [czlab.twisty.codec :refer [passwd<>]]
            [czlab.xlib.logging :as log])

  (:use [czlab.wabbit.io.loops]
        [czlab.wabbit.base.core]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.wabbit.io.core])

  (:import [czlab.wabbit.io IoService EmailEvent]
           [javax.mail.internet MimeMessage]
           [javax.mail
            Flags$Flag
            Flags
            Store
            Folder
            Session
            Provider
            Provider$Type]
           [czlab.wabbit.server Container]
           [java.util Properties]
           [java.io IOException]
           [czlab.xlib Muble Identifiable]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)
(def
  ^:dynamic
  *mock-mail-provider*
  {:pop3s "czlab.proto.mock.mail.MockPop3SSLStore"
   :imaps "czlab.proto.mock.mail.MockIMapSSLStore"
   :pop3 "czlab.proto.mock.mail.MockPop3Store"
   :imap "czlab.proto.mock.mail.MockIMapStore"})

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; POP3
(def ^:private cz-pop3s  "com.sun.mail.pop3.POP3SSLStore")
(def ^:private cz-pop3  "com.sun.mail.pop3.POP3Store")
(def ^:private pop3s "pop3s")
(def ^:private pop3c "pop3")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; IMAP
(def ^:private cz-imaps "com.sun.mail.imap.IMAPSSLStore")
(def ^:private cz-imap "com.sun.mail.imap.IMAPStore")
(def ^:private imaps "imaps")
(def ^:private imap "imap")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- closeFolder
  ""
  [^Folder fd]
  (if (some? fd)
    (try!
      (if (.isOpen fd) (.close fd true)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- closeStore
  ""
  [^IoService co]
  (let [{:keys [store folder]}
        (.intern (.getx co))]
    (closeFolder folder)
    (if (some? store)
      (try!
        (.close ^Store store)))
    (doto (.getx co)
      (.unsetv :store)
      (.unsetv :folder))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- resolveProvider
  ""
  [^IoService co ^String cz ^String proto]
  (let
    [mockp (sysProp "wabbit.mock.mail.proto")
     demo? (hgl? mockp)
     proto (if demo? mockp proto)
     kee (keyword proto)
     demop (kee *mock-mail-provider*)
     ss (-> (doto (Properties.)
              (.put  "mail.store.protocol" proto))
            (Session/getInstance nil))
     ps (.getProviders ss)
     [^Provider sun ^String pz]
     (if demo?
       [(Provider. Provider$Type/STORE
                   proto demop "czlab" "1.1.7") demop]
       [(some #(if (= cz
                      (.getClassName ^Provider %)) %)
              (seq ps)) cz])]
    (if (nil? sun)
      (throwIOE (str "Failed to find store: " pz)))
    (log/info "mail store impl = %s" sun)
    (.setProvider ss sun)
    (doto (.getx co)
      (.setv :proto proto)
      (.setv :session ss))
    co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- evt<>
  ""
  [^IoService co {:keys [msg]}]
  (let [eeid (str "event#" (seqint2))]
    (with-meta
      (reify EmailEvent
        (checkAuthenticity [_] false)
        (id [_] eeid)
        (source [_] co)
        (message [_] msg))
      {:typeid ::EmailEvent })))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- connectPop3
  ""
  [^IoService co]
  (let [{:keys [^Session session
                ^String proto]}
        (.intern (.getx co))
        {:keys [^String host
                port
                ^String user
                ^String passwd]}
        (.config co)]
    (when-some [s (.getStore session proto)]
      (.connect s
                host
                ^long port
                user
                (stror passwd nil))
      (doto (.getx co)
        (.setv :folder
               (some-> (.getDefaultFolder s)
                       (.getFolder "INBOX")))
        (.setv :store s))
      (let [fd (.getv (.getx co) :folder)]
        (when (or (nil? fd)
                  (not (.exists ^Folder fd)))
          (.unsetv (.getx co) :store)
          (try! (.close s))
          (throwIOE "cannot find inbox"))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- readPop3
  ""
  [^IoService co msgs]
  (let [d? (.getv (.getx co) :deleteMsg?)]
    (doseq [^MimeMessage mm  msgs]
      (doto mm
        (.getAllHeaders)
        (.getContent))
      (.dispatch co (ioevent<> co {:msg mm}))
      (when d? (.setFlag mm Flags$Flag/DELETED true)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- scanPop3
  ""
  [^IoService co]
  (let [{:keys [^Folder folder
                ^Store store]}
        (.intern (.getx co))]
    (if (and (some? folder)
             (not (.isOpen folder)))
      (.open folder Folder/READ_WRITE))
    (when (and (some? folder)
               (.isOpen folder))
      (try
        (let [cnt (.getMessageCount folder)]
          (log/debug "count of new mail-messages: %d" cnt)
          (if (spos? cnt)
            (readPop3 co (.getMessages folder))))
        (finally
          (try! (.close folder true)))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- wakeup
  ""
  [co]
  (try
    (connectPop3 co)
    (scanPop3 co)
    (catch Throwable e#
      (log/exception e#))
    (finally
      (closeStore co))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- sanitize
  ""
  [^IoService co {:keys [port deleteMsg?
                         host user ssl? passwd]
                  :as cfg0}]
  (let [pkey (.podKey (.server co))]
    (-> (assoc cfg0 :ssl? (not (false? ssl?)))
        (assoc :deleteMsg? (true? deleteMsg?))
        (assoc :host (str host))
        (assoc :port (if (spos? port) port 995))
        (assoc :user (str user ))
        (assoc :passwd (.text (passwd<> passwd pkey))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn POP3
  ""
  ^LifeCycle
  [co {:keys [conf] :as spec}]
  (let
    [impl (muble<>)
     funcs (threadedTimer {:wakeup wakeup<o>})]
    (reify LifeCycle
      (config [_] (.intern impl))
      (parent [_] co)
      (start [_ _] ((:start funcs) (.intern impl)))
      (stop [_] ((:stop funcs)))
      (init [_ arg]
        (let [c2 (merge conf
                        (sanitize co arg))
              [z p] (if (:ssl? c2)
                      [cz-pop3s pop3s]
                      [cz-pop3 pop3c])]
          (.copyEx impl c2)
          (resolveProvider co z p))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro ^:private connectIMAP "" [co] `(connectPop3 ~co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmacro ^:private scanIMAP "" [co] `(scanPop3 ~co))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- wakeup<i>
  ""
  [co]
  (try
    (connectIMAP co)
    (scanIMAP co)
    (catch Throwable e#
      (log/exception e#))
    (finally
      (closeStore co))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn IMAP
  ""
  ^LifeCycle
  [co {:keys [conf] :as spec}]
  (let
    [impl (muble<>)
     funcs (threadedTimer {:wakeup wakeup<i>})]
    (reify LifeCycle
      (config [_] (.intern impl))
      (parent [_] co)
      (start [_ _] ((:start funcs) (.intern impl)))
      (stop [_] ((:stop funcs)))
      (init [_ arg]
        (let [c2 (merge conf
                        (sanitize co arg))
              [z p] (if (:ssl? c2)
                      [cz-imaps imaps]
                      [cz-imap imap])]
          (.copyEx impl c2)
          (resolveProvider co z p))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

