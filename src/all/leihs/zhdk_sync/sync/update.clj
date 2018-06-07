(ns leihs.zhdk-sync.sync.update
  (:refer-clojure :exclude [str keyword])
  (:require 
    [leihs.utils.core :refer [presence str keyword]]
    [leihs.zhdk-sync.leihs-admin-api :as leihs-api]
    [leihs.zhdk-sync.sync.shared :refer :all]

    [cheshire.core :as cheshire]
    [progrock.core :as progrock]
    [clojure.java.io :as io]

    [timothypratley.patchin :refer [diff]]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I>]]
    [logbug.thrown :as thrown]
    ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn needs-update? [zapi-attrs leihs-attrs]
  (and leihs-attrs 
       (let [id (:id leihs-attrs)
             leihs-attrs (dissoc leihs-attrs :id)]
         (if (not= leihs-attrs zapi-attrs)
           (do (logging/info "To be updated " id ": " 
                             (diff leihs-attrs zapi-attrs))
               true)
           false))))

(defn to-be-updated-users-by-org-id [zapi-people leihs-users]
  (let [leihs-users-org-id-map (->> leihs-users 
                                    (filter :org_id)
                                    (map (fn [u] [(:org_id u)
                                                  (select-keys u attribute-keys)]))
                                    (into {}))]
    (->> zapi-people
         (map zapi->leihs-attributes)
         (filter :org_id)
         (filter (fn [zapi-attrs] 
                   (let [leihs-attrs (get leihs-users-org-id-map (:org_id zapi-attrs))]
                     (needs-update? zapi-attrs leihs-attrs))))
         (map #(assoc % :id (-> leihs-users-org-id-map 
                                (get (-> % :org_id))
                                :id))))))

(defn to-be-updated-users-by-email [zapi-people leihs-users]
  (let [leihs-users-email-map (->> leihs-users 
                                   (filter :email)
                                   (map (fn [u] [(clojure.string/lower-case (:email u))
                                                 (select-keys u attribute-keys)]))
                                   (into {}))]
    (->> zapi-people
         (map zapi->leihs-attributes)
         (filter :email)
         (filter (fn [zapi-attrs] 
                   (let [email (-> zapi-attrs :email clojure.string/lower-case)
                         leihs-attrs (get leihs-users-email-map email)]
                     (needs-update? zapi-attrs leihs-attrs))))
         (map #(assoc % :id (-> leihs-users-email-map 
                                (get (-> % :email clojure.string/lower-case))
                                :id))))))


(defn to-be-updated-users [conf zapi-people leihs-users]
  (case (:leihs-sync-id conf)
    "email" (to-be-updated-users-by-email zapi-people leihs-users)
    "org_id" (to-be-updated-users-by-org-id zapi-people leihs-users)))


(defn update-existing-leihs-users [conf zapi-people leihs-users]
  (logging/info ">>> Updating leihs users >>>")
  (let [show-progress (:progress conf)
        to-be-updated-users (to-be-updated-users conf zapi-people leihs-users)
        total-count (count to-be-updated-users)]
    (def ^:dynamic *to-be-updated-users* to-be-updated-users)
    (loop [users to-be-updated-users
           bar (progrock/progress-bar total-count)]
      (if-let [user (first users)]
        (do 
          (when show-progress (progrock/print bar) (flush))
          (if (:dry-run conf)
            (Thread/sleep 50)
            (leihs-api/update-user user conf))
          (recur (rest users) (progrock/tick bar)))
        (do
          (when show-progress
            (progrock/print (assoc bar 
                                   :done? true 
                                   :total  total-count
                                   :progress total-count))
            (flush)))))
    (logging/info "<<< Updated " total-count " leihs users <<<")
    to-be-updated-users))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
