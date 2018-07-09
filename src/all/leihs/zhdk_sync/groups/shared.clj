(ns leihs.zhdk-sync.groups.shared
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.utils.core :refer [presence str keyword]]
    [leihs.zhdk-sync.leihs-admin-api :as leihs-api]

    [cheshire.core :as cheshire]
    [progrock.core :as progrock]
    [clojure.java.io :as io]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I>]]
    [logbug.thrown :as thrown]
    ))


(defn get-zapi-field [zapi-group ks]
  (some-> zapi-group (get-in ks) presence))

(def attribute-keys
  [
   :id ;; we need the id to compute the patch address
   :name 
   :org_id
   ])

(defn zapi->leihs-attributes [zapi-group]
  (let [evento-id (:id zapi-group)]
    {:name (get-zapi-field zapi-group [:name])
     :org_id (str evento-id)}))
