;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlabclj.tpcl.boot

  (:require [clojure.tools.logging :as log]
            [clojure.java.io :as io]
            [clojure.string :as cstr]
            [czlabclj.tpcl.antlib])

  (:import [java.util Stack]
           [java.io File]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn FmtCljNsps "Format list of namespaces."

  [root & dirs]

  (reduce
    (fn [memo dir]
      (let [nsp (cstr/replace dir #"\/" ".")]
        (concat
          memo
          (map #(str nsp "." (cstr/replace (.getName %) #"\.[^\.]+$" ""))
               (filter #(and (.isFile %)(.endsWith (.getName %) ".clj"))
                       (.listFiles (io/file root dir)))))))
    []
    dirs
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn Babel "Run babel on the given arguments."

  [workingDir args]

  (let [pj (ant/AntProject)
        t1 (->> [[:args args ]]
                (ant/AntExec pj {:executable "babel"
                                 :dir workingDir})) ]
    (-> (ant/ProjAntTasks pj "babel" t1)
        (ant/ExecTarget))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BabelTree ""

  [rootDir cfgtor]

  (walk-tree cfgtor (Stack.) (io/file rootDir)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- walk-tree ""

  [cfgtor ^Stack stk seed]

  (doseq [f (-> (if-not (nil? seed) seed (.peek stk))
                (.listFiles))]
    (let [p (if (.empty stk)
              []
              (for [x (.toArray stk)] (.getName x)))
          fid (.getName f)
          paths (conj p fid) ]
      (if-let [rc (cfgtor f paths)]
        (if (.isDirectory f)
          (do
            (.push stk f)
            (walk-tree cfgtor stk nil))
          (when (.endsWith fid ".js")
            (Babel (:work-dir rc) (:args rc)))))))

  (when-not (.empty stk) (.pop stk)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF