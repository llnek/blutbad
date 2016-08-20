;; Licensed under the Apache License, Version 2.0 (the "License");
;; you may not use this file except in compliance with the License.
;; You may obtain a copy of the License at
;;
;;     http://www.apache.org/licenses/LICENSE-2.0
;;
;; Unless required by applicable law or agreed to in writing, software
;; distributed under the License is distributed on an "AS IS" BASIS,
;; WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
;; See the License for the specific language governing permissions and
;; limitations under the License.
;;
;; Copyright (c) 2013-2016, Kenneth Leung. All rights reserved.

(ns ^{:doc ""
      :author "Kenneth Leung" }

  czlab.skaro.sys.exec

  (:require
    [czlab.xlib.process :refer [async! safeWait]]
    [czlab.xlib.str :refer [strim hgl?]]
    [czlab.xlib.meta :refer [getCldr]]
    [czlab.xlib.mime :refer [setupCache]]
    [czlab.xlib.format :refer [readEdn]]
    [czlab.xlib.logging :as log]
    [clojure.java.io :as io]
    [czlab.xlib.files
     :refer [basename
             mkdirs
             readUrl
             listFiles]]
    [czlab.xlib.core
     :refer [test-nestr
             srandom<>
             convLong
             inst?
             fpath
             trylet!
             try!
             getCwd
             muble<>
             juid
             test-nonil]])

  (:use [czlab.skaro.sys.dfts]
        [czlab.skaro.io.config]
        [czlab.skaro.sys.core]
        [czlab.skaro.sys.ext]
        [czlab.skaro.jmx.core])

  (:import
    [czlab.skaro.loaders AppClassLoader]
    [java.security SecureRandom]
    [clojure.lang Atom]
    [java.util Date]
    [java.io File]
    [java.net URL]
    [czlab.xlib
     Disposable
     Startable
     Muble
     Versioned
     Hierarchial
     Identifiable]
    [czlab.skaro.server
     Container
     Execvisor
     AppGist
     JmxServer
     ServiceGist
     Service
     Component]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* false)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private START-TIME (.getTime (Date.)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- inspectPod

  "Make sure the pod setup is ok"
  ^AppGist
  [^Execvisor execv ^File des]

  (let [conf (io/file des CFG_APP_CF)
        app (basename des)]
    (log/info "app dir : %s\ninspecting..." des)
    (precondFile conf)
    (precondDir (io/file des DN_CONF)
                (io/file des DN_ETC))
    (let [ps (readEdn conf)
          ctx (.getx execv)]
      (log/info "checking conf for app: %s" app)
      ;; create the pod meta and register it
      ;; as a application
      (let [m (-> (podMeta app
                           ps
                           (io/as-url des)))]
        (comp->initialize m)
        (->> (-> (.getv ctx :apps)
                 (assoc (.id m) m))
             (.setv ctx :apps))
        m))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- startJmx

  "Basic JMX support"
  ^JmxServer
  [^Execvisor co cfg]

  (log/info "jmx-config:\n%s" cfg)
  (trylet!
    [port (or (:port cfg) 7777)
     host (str (:host cfg))
     jmx (jmxServer<> host)]
    (.setRegistryPort jmx (int port))
    (.start jmx)
    (.reg jmx co "com.zotohlab" "execvisor" ["root=skaro"])
    (-> (.getx co)
        (.setv :jmxServer jmx))
    (log/info "jmx-server listening on: %s:%s" host port)
    jmx))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopJmx

  "Kill the internal JMX server"
  ^Component
  [^Execvisor co]

  (trylet!
    [ctx (.getx co)
     jmx (.getv ctx :jmxServer)]
    (when (some? jmx)
      (.stop ^JmxServer jmx))
    (.unsetv ctx :jmxServer))
  (log/info "jmx connection terminated")
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ignitePod

  ""
  ^Execvisor
  [^Execvisor co ^AppGist gist]

  (async!
    #(trylet!
       [cc (.getv (.getx co) :containers)
        ^Container
        ctr (container<> gist)
        app (.id gist)
        cid (.id ctr)]
       (log/debug (str "start pod = %s\n instance = %s") app cid)
       (->> (assoc cc cid ctr)
            (.setv (.getx co) :containers)))
    {:classLoader (AppClassLoader. (getCldr))
     :daemon true}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- stopPods

  ""
  ^Execvisor
  [^Execvisor co]

  (log/info "preparing to stop pods...")
  (doseq [[_ ^Container v]
          (.getv (.getx co) :containers)]
    (.stop v)
    (.dispose v))
  (.setv (.getx co) :containers {})
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn execvisor<>

  "Create a Execvisor"
  ^Execvisor
  []

  (let [impl (muble<> {:containers {}
                       :apps {}
                       :emitters {}})
        pid (juid)]
    (with-meta
      (reify

        Execvisor

        (uptimeInMillis [_]
          (- (System/currentTimeMillis) START-TIME))
        (id [_] (format "%s{%s}" "execvisor" pid))
        (homeDir [_] (maybeDir impl :basedir))
        (version [_] "1.0")
        (getx [_] impl)
        (startTime [_] START-TIME)
        (kill9 [_] (apply (.getv impl :stopper) []))

        (start [this]
          (doseq [[_ v] (.getv impl :apps)]
            (ignitePod this v)))

        (stop [this]
          (stopJmx this)
          (stopPods this)))

       {:typeid ::Execvisor})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- emitMeta

  ""
  ^ServiceGist
  [emsType gist]

  (let [{:keys [info conf]}
        gist
        impl (muble<> conf)]
    (with-meta
      (reify

        ServiceGist

        (version [_] (:version info))
        (getx [_] impl)
        (type [_] emsType)

        (setParent [_ p] (.setv impl :execv p))
        (parent [_] (.getv impl :execv))

        (isEnabled [_]
          (not (false? (:enabled info))))

        (id [_] (:name info)))

      {:typeid  ::ServiceGist})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;description of a emitter
(defmethod comp->initialize

  ::ServiceGist
  [^Component co & [execv]]

  (log/info "comp->initialize: ServiceGist: %s" (.id co))
  (.setv (.getx co) :execv execv)
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;; Each emitter has a meta data file describing
;; its functions and features
;; This registry loads these meta files and adds them to the registry
(defn- regoEmitters

  ""
  ^Execvisor
  [^Execvisor co]

  (let [ctx (.getx co)]
    (->>
      (persistent!
        (reduce
          #(let [b (emitMeta (first %2)
                             (last %2))]
             (comp->initialize b )
             (assoc! %1 (.id b) b))
          (transient {})
          *emitter-defs*))
      (.setv ctx :emitters ))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- regoApps

  ""
  ^Execvisor
  [^Execvisor co]

  (inspectPod co (getCwd))
  co)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod comp->initialize

  ::Execvisor
  [^Execvisor co & [rootGist]]
  {:pre [(inst? Atom rootGist)]}

  (let [{:keys [basedir skaroConf]}
        @rootGist
        {:keys [jmx]}
        skaroConf]
    (log/info "com->initialize: Execvisor: %s" co)
    (test-nonil "conf file: jmx" jmx)

    (System/setProperty "file.encoding" "utf-8")
    (.copy (.getx co) (muble<> @rootGist))
    (->> "app/mime.properties"
         (io/file basedir DN_ETC)
         (io/as-url)
         (setupCache ))

    (regoEmitters co)
    (regoApps co)
    (startJmx co jmx)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

