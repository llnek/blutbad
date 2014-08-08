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
      :author "kenl"}

  cmzlabclj.tardis.demo.jms.core

  (:require [clojure.tools.logging :as log :only [info warn error debug] ]
            [clojure.string :as cstr])

  (:use [cmzlabclj.nucleus.util.process :only [DelayExec] ]
        [cmzlabclj.nucleus.util.core :only [notnil?] ]
        [cmzlabclj.nucleus.util.str :only [nsb] ]
        [cmzlabclj.tardis.core.sys :only [DefWFTask]])

  (:import  [com.zotohlab.wflow FlowNode PTask
                                PipelineDelegate]
            [com.zotohlab.gallifrey.io JMSEvent]
            [javax.jms TextMessage]
            [java.util.concurrent.atomic AtomicInteger]
            [com.zotohlab.gallifrey.core Container]
            [com.zotohlab.wflow.core Job]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ^AtomicInteger _count (AtomicInteger.))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- ncount ""

  []

  (.incrementAndGet _count))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype Demo [] PipelineDelegate

  (getStartActivity [_ pipe]
    (DefWFTask
      (fn [cur job arg]
        (let [^JMSEvent ev (.event job)
              ^TextMessage msg (.getMsg ev) ]
          (println "-> Correlation ID= " (.getJMSCorrelationID msg))
          (println "-> Msg ID= " (.getJMSMessageID msg))
          (println "-> Type= " (.getJMSType msg))
          (println "("
                   (ncount)
                   ") -> Message= "
                   (.getText msg))
          nil))))

  (onStop [_ pipe] )

  (onError [_ err c] nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(deftype DemoMain [] cmzlabclj.tardis.impl.ext.CljAppMain

  (contextualize [_ c] )

  (initialize [_]
    (println "Demo receiving JMS messages..." ))

  (configure [_ cfg] )

  (start [_] )
  (stop [_] )

  (dispose [_] ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private core-eof nil)

