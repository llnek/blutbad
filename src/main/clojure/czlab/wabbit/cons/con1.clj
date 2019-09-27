;; Copyright © 2013-2019, Kenneth Leung. All rights reserved.
;; The use and distribution terms for this software are covered by the
;; Eclipse Public License 1.0 (http://opensource.org/licenses/eclipse-1.0.php)
;; which can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any fashion, you are agreeing to be bound by
;; the terms of this license.
;; You must not remove this notice, or any other, from this software.

(ns
  ^{:doc ""
      :author "Kenneth Leung"}

  czlab.wabbit.cons.con1

  (:require [czlab.wabbit.core :as wc]
            [czlab.twisty.core :as tc]
            [czlab.twisty.codec :as co]
            [czlab.basal.log :as l]
            [czlab.antclj.antlib :as a]
            [clojure.java.io :as io]
            [io.aviso.ansi :as ansi]
            [clojure.string :as cs]
            [czlab.basal.io :as i]
            [czlab.basal.util :as u]
            [czlab.wabbit.exec :as we]
            [czlab.wabbit.cons.con2 :as c2]
            [czlab.basal.core :as c :refer [n#]])

  (:import [java.util
            ResourceBundle
            Properties
            Calendar
            Map
            Date]
           [java.io File]
           [org.apache.commons.io FileUtils]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)
(def ^:dynamic *config-object* nil)
(def ^:dynamic *pkey-object* nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn get-home-dir

  "The current directory."
  []

  (io/file (u/get-sys-prop "wabbit.user.dir")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- on-help-xxx

  "Print out help messages."
  [pfx end]

  (try (dotimes [n end]
         (c/prn!! "%s"
                  (u/rstr (wc/get-rc-base)
                          (str pfx (+ 1 n)))))
       (finally
         (c/prn!! ""))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- on-help-create

  "Help message for action: create."
  []

  (on-help-xxx "usage.new.d" 5))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn on-create

  "Create a new pod."
  [args]

  (if (empty? args)
    (u/throw-BadData "CmdError!")
    (apply c2/create-pod (c/_1 args) (drop 1 args))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- on-help-podify

  "Help messages for action: podify."
  []

  (on-help-xxx "usage.podify.d" 2))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn on-podify

  ""
  [args]

  (if (empty? args)
    (u/throw-BadData "CmdError!")
    (let [a (io/file (wc/get-proc-dir))
          dir (i/mkdirs (io/file (c/_1 args)))]
      (a/run* (a/zip {:includes "**/*"
                      :basedir a
                      :destFile (io/file dir (str (.getName a) ".zip"))})))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- on-help-start

  "Help message for action: start."
  []

  (on-help-xxx "usage.start.d" 4))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- on-help-stop

  "Help message for action: stop."
  []

  (on-help-xxx "usage.stop.d" 2))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- run-pod-bg

  "Run the application in the background."
  [podDir]

  (let [progW (io/file podDir "bin/wabbit.bat")
        prog (io/file podDir "bin/wabbit")
        tk (if (u/is-windows?)
             (a/exec {:dir podDir
                      :executable "cmd.exe"}
                     [[:argvalues ["/C" "start" "/B"
                                   "/MIN" (u/fpath progW) "run"]]]))]
    (if false (a/exec
                {:dir podDir
                 :executable (u/fpath prog)}
                [[:argvalues ["run" "bg"]]]))
    ;run the target
    (if tk
      (a/run* tk)
      (u/throw-BadData "CmdError!"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn on-start

  "Start the application."
  [args]

  (let [s2 (c/_1 args)
        cwd (wc/get-proc-dir)]
    ;; background job is handled differently on windows
    (if (and (u/is-windows?)
             (c/in? #{"-bg" "--background"} s2))
      (run-pod-bg cwd)
      (do (-> wc/banner
              ansi/bold-yellow c/prn!!) (we/start-via-cons cwd)))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn on-stop

  "" {:no-doc true} [args])

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- on-help-debug

  "Help message for action: debug."
  []

  (on-help-xxx "usage.debug.d" 2))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn on-debug

  "Debug the application."
  {:no-doc true}
  [args]

  (on-start args))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- on-help-demos

  "Help message for action :demo."
  []

  (on-help-xxx "usage.demo.d" 2))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn on-demos

  "Generate demo samples."
  {:no-doc true}
  [args]

  (if (empty? args)
    (u/throw-BadData "CmdError!")
    (c2/publish-samples (c/_1 args))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn gen-pwd

  "Generate a passord."
  [args]

  (let [c (c/_1 args)
        n (c/s->long (str c) 16)]
    (if-not (and (>= n 8)
                 (<= n 48))
      (u/throw-BadData "CmdError!")
      (-> n co/strong-pwd<> co/pw-text i/x->str))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn gen-guid

  "Generate a UUID."
  []

  (u/uuid<>))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- on-encrypt

  "Encrypt some data."
  [args]

  (let [[s k] (cond (c/one? args) [(c/_1 args) *pkey-object*]
                    (c/two? args) [(c/_2 args)(c/_1 args)]
                    :else (u/throw-BadData "CmdError!"))]
    (try (-> (co/pwd<> s k) co/pw-encoded i/x->str)
         (catch Throwable e (c/prn!! "Failed to encrypt: %s." (u/emsg e))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- on-decrypt

  "Decrypt some data."
  [args]

  (let [[s k] (cond (c/one? args) [(c/_1 args) *pkey-object*]
                    (c/two? args) [(c/_2 args)(c/_1 args)]
                    :else (u/throw-BadData "CmdError!"))]
    (try (-> (co/pwd<> s k) co/pw-text i/x->str)
         (catch Throwable e (c/prn!! "Failed to decrypt: %s." (u/emsg e))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- on-hash

  "Generate a hash/digest on the data."
  [args]

  (if-not (empty? args)
    (tc/gen-digest (c/_1 args))
    (u/throw-BadData "CmdError!")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- on-mac

  "Generate a MAC on the data."
  [args]

  (if (empty? args)
    (u/throw-BadData "CmdError!")
    (tc/gen-mac *pkey-object* (c/_1 args))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- on-help-generate

  "Help message for action: generate."
  []

  (on-help-xxx "usage.gen.d" 9))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn on-generate

  "Various generate functions."
  {:no-doc true}
  [args]

  (let [c (c/_1 args)
        args (drop 1 args)]
    (cond (c/in? #{"-p" "--password"} c) (gen-pwd args)
          (c/in? #{"-h" "--hash"} c) (on-hash args)
          (c/in? #{"-m" "--mac"} c) (on-mac args)
          (c/in? #{"-u" "--uuid"} c) (gen-guid)
          (c/in? #{"-e" "--encrypt"} c) (on-encrypt args)
          (c/in? #{"-d" "--decrypt"} c) (on-decrypt args)
          :else (u/throw-BadData "CmdError!"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn prn-generate

  "Print out result from the generate function."
  {:no-doc true}
  [args]

  (c/prn!! (on-generate args)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- on-help-test-jce

  "Help message for action: test-jce."
  []

  (on-help-xxx "usage.testjce.d" 2))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn on-test-jce

  "Assert JCE."
  {:no-doc true}
  [args]

  (let [rcb (wc/get-rc-base)]
    (tc/assert-jce)
    (c/prn!! (u/rstr rcb "usage.testjce.ok"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- on-help-version

  "Help message for action: version."
  []

  (on-help-xxx "usage.version.d" 2))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn on-version

  "Print out the version."
  {:no-doc true}
  [args]

  (let [rcb (wc/get-rc-base)]
    (->> (u/get-sys-prop "wabbit.version")
         (u/rstr rcb "usage.version.o1")
         (c/prn!! "%s" ))
    (->> (u/get-sys-prop "java.version")
         (u/rstr rcb "usage.version.o2")
         (c/prn!! "%s"))
    (c/prn!! "")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- scan-jars

  "Scan a directory for jar files and list the file paths."
  [out dir]

  (let [sep (u/get-sys-prop "line.separator")]
    (c/sbf+ out
            (c/sreduce<>
              #(c/sbf+ %1
                       (str "<classpathentry  "
                            "kind=\"lib\""
                            " path=\"" (u/fpath %2) "\"/>" sep))
              (i/list-files dir ".jar")))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- gen-eclipse-proj

  "Generate a eclipse project file."
  [pdir]

  (let [ec (io/file pdir "eclipse.projfiles")
        poddir (io/file pdir)
        pod (i/fname poddir)
        sb (c/sbf<>)]
    (i/mkdirs ec)
    (FileUtils/cleanDirectory ec)
    (i/spit-utf8
      (io/file ec ".project")
      (-> (i/res->str (str "czlab/wabbit/eclipse/java/project.txt"))
          (cs/replace "${APP.NAME}" pod)
          (cs/replace "${JAVA.TEST}"
                      (u/fpath (io/file poddir "src/test/java")))
          (cs/replace "${JAVA.SRC}"
                      (u/fpath (io/file poddir "src/main/java")))
          (cs/replace "${CLJ.TEST}"
                      (u/fpath (io/file poddir "src/test/clojure")))
          (cs/replace "${CLJ.SRC}"
                      (u/fpath (io/file poddir "src/main/clojure")))))
    (i/mkdirs (io/file poddir wc/dn-build "classes"))
    (doall (map (partial scan-jars sb)
                [(io/file (get-home-dir) wc/dn-dist)
                 (io/file (get-home-dir) wc/dn-lib)
                 (io/file poddir wc/dn-target)]))
    (i/spit-utf8
      (io/file ec ".classpath")
      (-> (i/res->str (str "czlab/wabbit/eclipse/java/classpath.txt"))
          (cs/replace "${CLASS.PATH.ENTRIES}" (str sb))))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- on-help-ide

  "Help message for action: ide."
  []

  (on-help-xxx "usage.ide.d" 4))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn on-ide

  "Generate project files for IDEs."
  {:no-doc true}
  [args]

  (if-not (and (not-empty args)
               (c/in? #{"-e" "--eclipse"} (c/_1 args)))
    (u/throw-BadData "CmdError!")
    (gen-eclipse-proj (wc/get-proc-dir))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn- on-help-service-specs

  "Help message for action: service."
  []

  (on-help-xxx "usage.svc.d" 8))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn on-service-specs

  "Print out specs for built-in plugins."
  [args]

  (let [clj (u/cljrt<>)]
    (-> (c/preduce<map>
          #(assoc! %1
                   (c/_1 %2)
                   (u/var* clj
                           (str "czlab.wabbit.plugs."
                                (c/kw->str (c/_2 %2)))))
          {:OnceTimer :loops/OnceTimerSpec
           :FilePicker :files/FilePickerSpec
           :SocketIO :socket/SocketIOSpec
           :JMS :jms/JMSSpec
           :POP3 :mails/POP3Spec
           :IMAP :mails/IMAPSpec
           :HTTP :http/HTTPSpec
           :RepeatingTimer :loops/RepeatingTimerSpec}) i/fmt->edn c/prn!!)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn on-help-help

  "Help message for action: help."
  []

  (u/throw-BadData "CmdError!"))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(def wabbit-tasks nil)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(defn on-help

  "Print out help message for an action."
  {:no-doc true}
  [args]

  (c/if-fn? [h (c/_2 (wabbit-tasks
                       (keyword (c/_1 args))))]
    (h)
    (u/throw-BadData "CmdError!")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
(alter-var-root
  #'wabbit-tasks
  (fn [_]
    {:service [on-service-specs on-help-service-specs]
     :new [on-create on-help-create]
     :ide [on-ide on-help-ide]
     :podify [on-podify on-help-podify]
     :debug [on-debug on-help-debug]
     :help [on-help on-help-help]
     :run [on-start on-help-start]
     :stop [on-stop on-help-stop]
     :demos [on-demos on-help-demos]
     :crypto [prn-generate on-help-generate]
     :testjce [on-test-jce on-help-test-jce]
     :version [on-version on-help-version]}))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF
