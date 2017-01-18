;; Copyright (c) 2013-2017, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns ^{:doc "Basic functions for loopable services."
      :author "Kenneth Leung"}

  czlab.wabbit.io.loops

  (:require [czlab.xlib.dates :refer [parseDate]]
            [czlab.xlib.process :refer [async!]]
            [czlab.xlib.meta :refer [getCldr]]
            [czlab.xlib.logging :as log])

  (:use [czlab.wabbit.base.core]
        [czlab.xlib.core]
        [czlab.xlib.str]
        [czlab.wabbit.io.core])

  (:import [czlab.wabbit.io IoService TimerEvent]
           [java.util Date Timer TimerTask]
           [clojure.lang APersistentMap]
           [czlab.xlib Muble LifeCycle]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- configRepeat
  ""
  [^Timer timer delays ^long intv func]
  (log/info "Scheduling a *repeating* timer: %dms" intv)
  (let
    [tt (tmtask<> func)
     [dw ds] delays]
    (if (spos? intv)
      (cond
        (inst? Date dw)
        (.schedule tm tt ^Date dw intv)
        :else
        (.schedule tm
                   tt
                   (long (if (> ds 0) ds 1000)) intv)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- configOnce
  ""
  [^Timer timer delays func]
  (log/info "Scheduling a *one-shot* timer at %s" delays)
  (let
    [tt (tmtask<> func)
     [dw ds] delays]
    (cond
      (inst? Date dw)
      (.schedule timer tt ^Date dw)
      :else
      (.schedule timer
                 tt
                 (long (if (> ds 0) ds 1000))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- configTimer
  [timer wakeup {:keys [intervalSecs
                        delayWhen
                        delaySecs] :as cfg} repeat?]
  (let
    [d [delayWhen (s2ms delaySecs)]]
    (test-some "java-timer" timer)
    (if (and repeat?
             (spos? intervalSecs))
      (configRepeat timer
                    d
                    (s2ms intervalSecs) wakeup)
      (configOnce timer d wakeup))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn threadedTimer
  ""
  [funcs]
  (let
    [wake (or (:wakeup funcs)
              (constantly nil))
     loopy (volatile! true)
     schedule
     (or (:schedule funcs)
         #(async!
            #(while @loopy
               (wake)
               (safeWait (:intervalMillis %)))
            {:cl (getCldr)}))]
    (doto
      {:start
       #(let
          [{:keys [intervalSecs
                   delaySecs delayWhen]}
           %
           func #(schedule {:intervalMillis
                            (s2ms intervalSecs)})]
          (if (or (spos? delaySecs)
                  (inst? Date delayWhen))
            (configOnce (Timer.)
                        [delayWhen (s2ms delaySecs)] func)
            (func)))
       :stop
       #(vreset! loopy false)})))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- evt<>
  ^TimerEvent
  [co repeat?]
  (let [eeid (str "event#"
                  (seqint2))]
    (reify
      TimerEvent
      (checkAuthenticity [_] false)
      (id [_] eeid)
      (source [_] co)
      (isRepeating [_] repeat?))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- xxxTimer<>
  ""
  [co {:keys [conf] :as spec} repeat?]
  (let
    [impl (muble<>)
     tee (keyword (juid))
     stop #(do (try! (some-> ^Timer
                             (.getv impl tee)
                             (.cancel)))
               (.unsetv impl tee))
     wakeup #(do (.dispatch co
                            (evt<> co repeat?))
                 (if-not repeat? (stop)))]
    (reify
      LifeCycle
      (config [_] (dissoc (.intern impl) tee))
      (parent [_] co)
      (init [_ arg]
        (.copyEx impl (merge conf cfg0)))
      (start [_ arg]
        (let [t (Timer. true)]
          (.setv impl tee t)
          (configTimer t wakeup repeat?)))
      (stop [_] stop))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RepeatingTimer
  ""
  [co spec]
  (xxxTimer<> co spec true))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn OnceTimer
  ""
  [co spec]
  (xxxTimer<> co spec false))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

