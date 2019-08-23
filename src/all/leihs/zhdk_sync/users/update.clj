(ns leihs.zhdk-sync.users.update
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.utils.core :refer [presence str keyword]]
    [leihs.zhdk-sync.leihs-admin-api :as leihs-api]
    [leihs.zhdk-sync.users.shared :refer :all]
    [leihs.zhdk-sync.users.photo :as photo]

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


(defn to-be-updated-attributes [conf org_id->leihs-users zapi-user-attributes]
  (when-let [leihs-user (get org_id->leihs-users (:org_id zapi-user-attributes))]
    (some-> zapi-user-attributes
            (#(diff leihs-user %))
            second
            (dissoc :img256_url :img32_url)
            not-empty
            (assoc :id (:id leihs-user))
            (#(if (:img_digest %)
                (assoc % 
                       :img256_url (:img256_url zapi-user-attributes)
                       :img32_url (:img32_url zapi-user-attributes))
                %)))))


(defn to-be-updated-users [conf zapi-people leihs-users]
  (let [org_id->leihs-users (->> leihs-users
                                 (filter :org_id)
                                 (map (fn [u] [(:org_id u) 
                                               (dissoc u :img256_url :img32_url)]))
                                 (into {}))]
    (->> zapi-people
         (map zapi->leihs-attributes)
         (filter :org_id)
         (map (partial to-be-updated-attributes conf org_id->leihs-users))
         (filter identity))))

(defn update-existing-leihs-users [conf zapi-people leihs-users]
  (logging/info ">>> Updating leihs users >>>")
  (let [show-progress (:progress conf)
        to-be-updated-users (to-be-updated-users conf zapi-people leihs-users)
        total-count (count to-be-updated-users)]
    (def ^:dynamic *to-be-updated-users* to-be-updated-users)
    (loop [users to-be-updated-users
           updated-users []
           bar (progrock/progress-bar total-count)]
      (if-let [user (first users)]
        (do (when show-progress (progrock/print bar) (flush))
            (let [updated-user (if (:dry-run conf)
                                 (do (Thread/sleep 50) {})
                                 (->> user
                                      (#(if (:img_digest %)
                                          (photo/update-images conf %)
                                          %))
                                      (leihs-api/update-user conf)
                                      :body))]
              (recur (rest users) 
                     (conj updated-users updated-user) 
                     (progrock/tick bar))))
        (do (when show-progress
              (progrock/print (assoc bar
                                     :done? true
                                     :total  total-count
                                     :progress total-count))
              (flush))
            (logging/info "<<< Updated " total-count " leihs users <<<")
            updated-users)))))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
