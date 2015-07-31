;; This library is distributed in  the hope that it will be useful but without
;; any  warranty; without  even  the  implied  warranty of  merchantability or
;; fitness for a particular purpose.
;; The use and distribution terms for this software are covered by the Eclipse
;; Public License 1.0  (http://opensource.org/licenses/eclipse-1.0.php)  which
;; can be found in the file epl-v10.html at the root of this distribution.
;; By using this software in any  fashion, you are agreeing to be bound by the
;; terms of this license. You  must not remove this notice, or any other, from
;; this software.
;; Copyright (c) 2013-2015, Ken Leung. All rights reserved.

(ns ^{:doc ""
      :author "kenl" }

  czlabclj.xlib.dbio.postgresql

  (:require [czlabclj.xlib.util.logging :as log])

  (:use [czlabclj.xlib.dbio.drivers]
        [czlabclj.xlib.dbio.core :as dbcore]))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;(set! *warn-on-reflection* true)

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defonce POSTGRESQL-URL "jdbc:postgresql://{{host}}:{{port}}/{{db}}" )
(defonce POSTGRESQL-DRIVER "org.postgresql.Driver")


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
;; Postgresql
(defmethod GetTSKeyword :postgresql [db] "TIMESTAMP WITH TIME ZONE")
(defmethod GetBlobKeyword :postgresql [db] "BYTEA")
(defmethod GetDoubleKeyword :postgresql [db] "DOUBLE PRECISION")
(defmethod GetFloatKeyword :postgresql [db] "REAL")

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenCal :postgresql

  [db fld]

  (GenTimestamp db fld))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenAutoInteger :postgresql

  [db table fld]

  (GenColDef db (GenCol fld) "SERIAL" false nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenAutoLong :postgresql

  [db table fld]

  (GenColDef db (GenCol fld) "BIGSERIAL" false nil))

;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;
(defmethod GenDrop :postgresql

  [db table]

  (str "DROP TABLE IF EXISTS " table " CASCADE" (GenExec db) "\n\n"))

;;(def XXX (.getMetas (MakeMetaCache testschema)))
;;(println (GetDDL (MakeMetaCache testschema) (Postgresql.) ))
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;EOF

