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

(ns ^{ :doc ""
       :author "kenl" }

  cmzlabsclj.tardis.etc.cli

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:require [clojure.data.json :as json])
  (:require [clojure.string :as cstr])

  (:use [cmzlabsclj.nucleus.util.core :only [GetUser juid IsWindows?] ])
  (:use [cmzlabsclj.nucleus.util.str :only [strim nsb] ])
  (:use [cmzlabsclj.nucleus.util.ini :only [ParseInifile] ])
  (:use [cmzlabsclj.nucleus.util.files :only [Unzip Mkdirs] ])
  (:use [cmzlabsclj.tardis.core.constants])
  (:use [cmzlabsclj.tardis.core.sys])

  (:use [cmzlabsclj.tardis.etc.task])

  (:import (org.apache.commons.io.filefilter FileFileFilter FileFilterUtils))
  (:import (org.apache.commons.lang3 StringUtils))
  ;;(:import (org.apache.commons.io FilenameUtils FileUtils))
  (:import (org.apache.commons.io FilenameUtils FileUtils))
  (:import (java.util UUID))
  (:import (java.io File)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:dynamic *SKARO-WEBCSSLANG* "scss")
;;(def ^:dynamic *SKARO-WEBLANG* "coffee")
(def ^:dynamic *SKARO-WEBLANG* "js")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- copy-files ""

  [^File srcDir ^File destDir ext]

  (FileUtils/copyDirectory srcDir
                           destDir
                           (FileFilterUtils/andFileFilter FileFileFilter/FILE
                                                          (FileFilterUtils/suffixFileFilter (str "." ext)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- sanitizeAppDomain ""

  [appDomain]

  (-> appDomain
      (StringUtils/stripStart ".")
      (StringUtils/stripEnd ".")
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn RunAppBg ""

  [^File hhhHome bg]

  (let [ prog2 (-> (File. hhhHome "bin/skaro.bat")(.getCanonicalPath ))
         prog (-> (File. hhhHome "bin/skaro")(.getCanonicalPath))
         pj (if (IsWindows?)
                (MakeExecTask "cmd.exe"
                              hhhHome
                              [ "/C" "start" "/B" "/MIN" prog2 "start" ])
                (MakeExecTask prog hhhHome [ "start" "bg" ])) ]
    (ExecProj pj)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn BundleApp ""

  [^File hhhHome appId]

  (let [ pod (File. hhhHome (str "pods/" appId ".pod"))
         srcDir (File. hhhHome (str "apps/" appId))
         pj (MakeZipTask srcDir pod [] [ "build.output.folder/**" ]) ]
    (ExecProj pj)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn AntBuildApp ""

  [^File hhhHome appId antTarget]

  (let [ pj (MakeAntTask hhhHome appId antTarget) ]
    (ExecProj pj)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CleanAppClasses ""

  [^File webzDir ^File czDir]

  (FileUtils/cleanDirectory webzDir)
  (FileUtils/cleanDirectory czDir)
  (Mkdirs czDir))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CreateDemo "Unzip the demo pod."

  [^File hhhHome demoId]

  (let [ fp (File. hhhHome (str "docs/samples/" demoId ".pod"))
         dest (File. hhhHome (str "apps/demo-" demoId)) ]
    (log/debug "Unzipping demo pod: " demoId)
    (when (.exists fp)
          (Mkdirs dest)
          (Unzip fp dest))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CreateSamples "Unzip all samples."

  [^File hhhHome]

  (let [ top (File. hhhHome (str "docs/samples"))
         fs (.listFiles top) ]
    (log/debug "Unzipping all samples.")
    (doseq [ ^File f (seq fs) ]
      (when (and (.isFile f)
                 (.endsWith (.getName f) ".pod"))
            (CreateDemo hhhHome (FilenameUtils/getBaseName (nsb f)))))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkcljfp ""

  ^File
  [^File cljd  ^String file]

  (File. cljd file))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- mkcljd ""

  ^File
  [^File appDir ^String appDomain]

  (File. appDir (str "src/main/clojure/" (.replace appDomain "." "/"))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- post-create-app ""

  [^File hhhHome appId ^String appDomain]

  (let [ h2db (str (if (IsWindows?) "/c:/temp/" "/tmp/") (juid))
         appDir (File. hhhHome (str "apps/" appId))
         appDomainPath (.replace appDomain "." "/")
         cljd (mkcljd appDir appDomain) ]
    (Mkdirs (File. h2db))
    (with-local-vars [ fp nil ]
      (var-set fp (mkcljfp cljd "core.clj"))
      (FileUtils/writeStringToFile ^File @fp
                                   (-> (FileUtils/readFileToString ^File @fp "utf-8")
                                       (StringUtils/replace "@@APPDOMAIN@@" appDomain))
                                   "utf-8")

      (var-set fp (mkcljfp cljd "pipe.clj"))
      (FileUtils/writeStringToFile ^File @fp
                                   (-> (FileUtils/readFileToString ^File @fp "utf-8")
                                       (StringUtils/replace "@@APPDOMAIN@@" appDomain))
                                   "utf-8")

      (var-set fp (File. appDir "conf/env.conf"))
      (FileUtils/writeStringToFile ^File @fp
                                   (-> (FileUtils/readFileToString ^File @fp "utf-8")
                                       (StringUtils/replace "@@H2DBPATH@@"
                                                            (str h2db "/" appId))
                                       (StringUtils/replace "@@APPDOMAIN@@" appDomain))
                                   "utf-8")

      (var-set fp (File. appDir "build.xml"))
      (let [ s (str "<arg value=\"" appDomain ".core\"/>"
                    "<arg value=\"" appDomain ".pipe\"/>" ) ]
        (FileUtils/writeStringToFile ^File @fp
                                     (-> (FileUtils/readFileToString ^File @fp "utf-8")
                                         (StringUtils/replace "@@APPCLJFILES@@" s)
                                         (StringUtils/replace "@@APPDOMAIN@@" appDomain)
                                         (StringUtils/replace "@@APPID@@" appId))
                                     "utf-8"))
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- create-app-common ""

  [^File hhhHome appId ^String appDomain flavor]

  (let [ appDir (Mkdirs (File. hhhHome (str "apps/" appId)))
         cfd (File. appDir "conf")
         mfDir (Mkdirs (File. appDir "META-INF"))
         appDomainPath (.replace appDomain "." "/") ]
    (with-local-vars [ fp nil ]

      (doseq [ s ["classes" "patch" "lib"]]
        (Mkdirs (File. appDir (str "POD-INF/" s))))

      (doseq [ s ["RELEASE-NOTES.txt" "NOTES.txt"
                  "LICENSE.txt" "README.md"]]
        (FileUtils/touch (File. mfDir ^String s)))

      (FileUtils/copyFileToDirectory (File. hhhHome "etc/app/build.xml")
                                     (File. hhhHome (str "apps/" appId)))
      (FileUtils/copyFileToDirectory (File. hhhHome "etc/app/MANIFEST.MF")
                                     mfDir)

      (Mkdirs (File. appDir "modules"))
      (Mkdirs cfd)
      (Mkdirs (File. appDir "docs"))

      (doseq [ s ["app.conf" "env.conf" "shiro.ini"]]
        (FileUtils/copyFileToDirectory (File. hhhHome (str "etc/app/" s))
                                       cfd))

      (var-set fp (File. cfd "app.conf"))
      (FileUtils/writeStringToFile ^File @fp
                                   (-> (FileUtils/readFileToString ^File @fp "utf-8")
                                       (StringUtils/replace "@@USER@@" (GetUser)))
                                   "utf-8")

      (doseq [ s [ "java" (str "clojure/" appDomainPath) ]]
        (Mkdirs (File. appDir (str "src/main/" s)))
        (Mkdirs (File. appDir (str "src/test/" s))))

      (FileUtils/copyFileToDirectory (File. hhhHome "etc/app/core.clj") (mkcljd appDir appDomain))
      (FileUtils/copyFileToDirectory (File. hhhHome "etc/app/pipe.clj") (mkcljd appDir appDomain))

      (Mkdirs (File. appDir "src/main/resources"))

      (doseq [ s ["build.xs" "ivy.config.xml" "ivy.xml" "pom.xml"]]
        (FileUtils/copyFileToDirectory (File. hhhHome (str "etc/app/" s))
                                       appDir))

      (var-set fp (File. mfDir "MANIFEST.MF"))
      (FileUtils/writeStringToFile ^File @fp
                                   (-> (FileUtils/readFileToString ^File @fp "utf-8")
                                       (StringUtils/replace "@@APPKEY@@"
                                                            (nsb (UUID/randomUUID)))
                                       (StringUtils/replace "@@APPMAINCLASS@@"
                                                            (str appDomain ".core.MyAppMain")))
                                   "utf-8")

      (var-set fp (File. appDir "pom.xml"))
      (FileUtils/writeStringToFile ^File @fp
                                   (-> (FileUtils/readFileToString ^File @fp "utf-8")
                                       (StringUtils/replace "@@APPDOMAIN@@" appDomain)
                                       (StringUtils/replace "@@APPID@@" appId))
                                   "utf-8")

      (var-set fp (File. appDir "ivy.xml"))
      (FileUtils/writeStringToFile ^File @fp
                                   (-> (FileUtils/readFileToString ^File @fp "utf-8")
                                       (StringUtils/replace "@@APPDOMAIN@@" appDomain)
                                       (StringUtils/replace "@@APPID@@" appId))
                                   "utf-8")

      (var-set fp (File. appDir "build.xs"))
      (FileUtils/writeStringToFile ^File @fp
                                   (-> (FileUtils/readFileToString ^File @fp "utf-8")
                                       (StringUtils/replace "@@WEBCSSLANG@@" *SKARO-WEBCSSLANG*)
                                       (StringUtils/replace "@@WEBLANG@@" *SKARO-WEBLANG*)
                                       (StringUtils/replace "@@APPTYPE@@" flavor)
                                       (StringUtils/replace "@@SKAROHOME@@" (.getCanonicalPath hhhHome)))
                                   "utf-8")
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CreateBasic ""

  [^File hhhHome appId ^String appDomain]

  (create-app-common hhhHome appId appDomain "basic")
  (post-create-app hhhHome appId appDomain))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- create-web-common ""

  [^File hhhHome appId ^String appDomain]

  (let [ wfc (File. hhhHome (str DN_CFG "/app/weblibs.conf" ))
         wbs (json/read-str (FileUtils/readFileToString wfc "utf-8")
                            :key-fn keyword)
         appDir (File. hhhHome (str "apps/" appId))
         wlib (Mkdirs (File. appDir "public/vendors"))
         csslg *SKARO-WEBCSSLANG*
         wlg *SKARO-WEBLANG*
         buf (StringBuilder.)
         appDomainPath (.replace appDomain "." "/") ]

    (doseq [ s ["pages" "media" "scripts" "styles"]]
      (Mkdirs (File. appDir (str "src/web/main/" s))))

    (doseq [ s ["pages" "media" "scripts" "styles"]]
      (Mkdirs (File. appDir (str "public/" s))))

    (FileUtils/copyFileToDirectory (File. hhhHome "etc/web/pipe.clj")
                                   (mkcljd appDir appDomain))
    (FileUtils/copyFileToDirectory (File. hhhHome "etc/web/cljsc.clj")
                                   (File. appDir "conf"))
    (FileUtils/copyFileToDirectory (File. hhhHome "etc/web/favicon.png")
                                   (File. appDir "src/web/main/media"))

    (Mkdirs (File. appDir "src/test/js"))

    (FileUtils/copyFile wfc (File. wlib ".list"))
    (doseq [ df (:libs wbs) ]
      (let [ dn (:dir df)
             dd (File. hhhHome (str "etc/weblibs/" dn))
             td (File. wlib ^String dn) ]
        (when (.isDirectory dd)
          (FileUtils/copyDirectoryToDirectory dd wlib)
          (when-not (:skip df)
            (doseq [ ^String f (:js df) ]
              (-> buf
                  (.append (FileUtils/readFileToString (File. td f) "utf-8"))
                  (.append (str "\n\n/* @@@" f "@@@ */"))
                  (.append "\n\n")))))))

    (FileUtils/writeStringToFile (File. appDir "public/c/webcommon.js")
                                 (nsb buf)
                                 "utf-8")

    (FileUtils/writeStringToFile (File. appDir "public/c/webcommon.css")
                                 ""
                                 "utf-8")

  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CreateJetty ""

  [^File hhhHome appId ^String appDomain]

  (let [ appDir (File. hhhHome (str "apps/" appId)) ]
    (create-app-common hhhHome appId appDomain "web")
    (create-web-common hhhHome appId appDomain)
    (doseq [ s [ "classes" "lib" ]]
      (Mkdirs (File. appDir (str "WEB-INF/" s))))
    (FileUtils/copyFile (File. hhhHome "etc/jetty/jetty.conf")
                        (File. appDir "conf/env.conf"))
    (FileUtils/copyFileToDirectory (File. hhhHome "etc/jetty/web.xml")
                                   (File. appDir "WEB-INF"))
    (post-create-app hhhHome appId appDomain)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn CreateWeb ""

  [^File hhhHome appId ^String appDomain]

  (let [ appDir (File. hhhHome (str "apps/" appId))
         appDomainPath (.replace appDomain "." "/")
         cfd (File. appDir "conf") ]
    (with-local-vars [fp nil]
      (create-app-common hhhHome appId appDomain "web")
      (create-web-common hhhHome appId appDomain)
      (copy-files (File. hhhHome "etc/netty") cfd "conf")
      (FileUtils/copyFileToDirectory (File. hhhHome "etc/netty/static-routes.conf")
                                     cfd)
      (FileUtils/copyFileToDirectory (File. hhhHome "etc/netty/routes.conf")
                                     cfd)

      (doseq [ s ["errors" "htmls"]]
        (Mkdirs (File. appDir (str "pages/" s))))

      (doto (File. hhhHome "etc/netty")
            (copy-files (File. appDir "pages/errors") ".err")
            (copy-files (File. appDir "pages/htmls") "ftl"))

      (FileUtils/copyFileToDirectory (File. hhhHome "etc/netty/index.html")
                                     (File. appDir "src/web/main/pages"))

      (var-set fp (File. appDir "conf/routes.conf"))

      (FileUtils/writeStringToFile ^File @fp
                                   (-> (FileUtils/readFileToString ^File @fp "utf-8")
                                       (StringUtils/replace "@@APPDOMAIN@@" appDomain))
                                   "utf-8")

      (FileUtils/copyFileToDirectory (File. hhhHome "etc/netty/pipe.clj")
                                     (mkcljd appDir appDomain))

      (post-create-app hhhHome appId appDomain)
  )))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private cli-eof nil)
