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

(ns ^{ :doc "Functions to load and query a .ini file."
       :author "kenl" }

  comzotohlabscljc.util.ini

  (:require [clojure.tools.logging :as log :only [info warn error debug] ])
  (:use [comzotohlabscljc.util.core
         :only [ThrowBadData ThrowIOE ConvBool ConvLong ConvDouble] ])
  (:require [clojure.string :as cstr])
  (:use [ comzotohlabscljc.util.files :only [FileRead?] ])
  (:use [ comzotohlabscljc.util.str :only [nsb strim] ])
  (:import (com.zotohlabs.frwk.util NCOrderedMap))
  (:import (org.apache.commons.lang3 StringUtils))
  (:import (java.net URL))
  (:import (java.io File IOException InputStreamReader
                    LineNumberReader PrintStream))
  (:import (java.util Map)))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defprotocol IWin32Conf

  "A Windows INI file object."

  (getSection [_ sectionName] )
  (sectionKeys [_ ] )
  (dbgShow [_])
  (getString [_ sectionName property] )
  (getLong [_ sectionName property] )
  (getBool [_ sectionName property] )
  (getDouble [_ sectionName property] )
  (optString [_ sectionName property dft] )
  (optLong [_ sectionName property dft] )
  (optBool [_ sectionName property dft] )
  (optDouble [_ sectionName property dft] ) )

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmulti ^comzotohlabscljc.util.ini.IWin32Conf ParseInifile "Parse a INI config file." class)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- throwBadIni ""

  [^LineNumberReader rdr]

  (ThrowBadData (str "Bad ini line: " (.getLineNumber rdr))))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- throwBadKey

  [k]

  (ThrowBadData (str "No such property " k ".")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- throwBadMap

  [s]

  (ThrowBadData (str "No such section " s ".")))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeSection

  [^LineNumberReader rdr ^Map ncmap ^String line]

  (let [ s (strim (StringUtils/strip line "[]")) ]
    (when (cstr/blank? s) (throwBadIni rdr))
    (when-not (.containsKey ncmap s) (.put ncmap s (NCOrderedMap.)))
    s
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- maybeLine

  [^LineNumberReader rdr ^Map ncmap ^Map section ^String line]

  (let [ ^Map kvs (.get ncmap section) ]
    (when (nil? kvs) (throwBadIni rdr))
    (let [ pos (.indexOf line (int \=))
           nm (if (> pos 0) (strim (.substring line 0 pos)) "" ) ]
      (when (cstr/blank? nm) (throwBadIni rdr))
      (.put kvs nm (strim (.substring line (+ pos 1)))) )
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- evalOneLine

  ^String
  [^LineNumberReader rdr ^Map ncmap ^String line ^String curSec]

  (let [ ln (strim line) ]
    (cond
      (or (cstr/blank? ln) (.startsWith ln "#"))
      curSec

      (.matches ln "^\\[.*\\]$")
      (maybeSection rdr ncmap ln)

      :else
      (do (maybeLine rdr ncmap curSec ln) curSec))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- hasKV ""

  [^Map m k]

  (let [ kn (name k) ]
    (if (or (nil? kn)
            (nil? m))
        nil
        (.containsKey m kn))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- getKV ""

  ^String
  [^comzotohlabscljc.util.ini.IWin32Conf cf s k err]

  (let [ kn (name k)
         sn (name s)
         ^Map mp (.getSection cf sn) ]
    (cond
      (nil? mp) (if err (throwBadMap sn) nil)
      (nil? k) (if err (throwBadKey "") nil)
      (not (hasKV mp k)) (if err (throwBadKey kn) nil)
      :else (nsb (.get mp kn)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- makeWinini ""

  [^Map mapOfSections]

  (reify IWin32Conf

    (getSection [_ sectionName]
      (let [ sn (name sectionName) ]
        (if (cstr/blank? sn)
            nil
            (let [ m (.get mapOfSections sn) ]
              (if (nil? m) nil (into {} m))))))

    (sectionKeys [_] (.keySet mapOfSections))

    (getString [this section property]
      (nsb (getKV this section property true)))

    (optString [this section property dft]
      (let [ rc (getKV this section property false) ]
        (if (nil? rc) dft rc)))

    (getLong [this section property]
      (ConvLong (getKV this section property true) 0))

    (optLong [this section property dft]
      (let [ rc (getKV this section property false) ]
        (if (nil? rc)
            dft
            (ConvLong rc 0))))

    (getDouble [this section property]
      (ConvDouble (getKV this section property true) 0.0))

    (optDouble [this section property dft]
      (let [ rc (getKV this section property false) ]
        (if (nil? rc)
            dft
            (ConvDouble rc 0.0))))

    (getBool [this section property]
      (ConvBool (getKV this section property true) false))

    (optBool [this section property dft]
      (let [ rc (getKV this section property false) ]
        (if (nil? rc)
            dft
            (ConvBool rc false))))

    (dbgShow [_]
      (let  [ buf (StringBuilder.) ]
        (doseq [ [k v] (seq mapOfSections) ]
          (.append buf (str "[" (name k) "]\n"))
          (doseq [ [x y] (seq v) ]
            (.append buf (str (name x) "=" y)))
          (.append buf "\n"))
        (println buf)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ParseInifile String

  ^comzotohlabscljc.util.ini.IWin32Conf
  [^String fpath]

  (if (nil? fpath)
      nil
      (ParseInifile (File. fpath))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ParseInifile File

  ^comzotohlabscljc.util.ini.IWin32Conf
  [^File file]

  (if (or (nil? file)
          (not (FileRead? file)))
      nil
      (ParseInifile (.toURL (.toURI file)))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defn- parseFile

  ^comzotohlabscljc.util.ini.IWin32Conf
  [^URL fUrl]

  (with-open [ inp (.openStream fUrl) ]
    (loop [ rdr (LineNumberReader. (InputStreamReader. inp "utf-8"))
            total (NCOrderedMap.)
            curSec ""
            line (.readLine rdr)  ]
      (if (nil? line)
          (makeWinini total)
          (recur rdr total (evalOneLine rdr total line curSec)
                           (.readLine rdr) )
      ))
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod ParseInifile URL

  ^comzotohlabscljc.util.ini.IWin32Conf
  [^URL fileUrl]

  (if (nil? fileUrl)
      nil
      (parseFile fileUrl)
  ))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(def ^:private ini-eof nil)

