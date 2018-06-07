(ns leihs.zhdk-sync.sync.add
  (:refer-clojure :exclude [str keyword])
  (:require 
    [leihs.utils.core :refer [presence str keyword]]
    [leihs.zhdk-sync.leihs-admin-api :as leihs-api]
    [leihs.zhdk-sync.sync.shared :refer :all]

    [cheshire.core :as cheshire]
    [progrock.core :as progrock]
    [clojure.java.io :as io]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I>]]
    [logbug.thrown :as thrown]
    ))


(defn- to-be-added-zapi-users-by-email [zapi-people leihs-users]
  (let [to-be-added-email-addresses (clojure.set/difference 
                                      (zapi-lower-email-addresses zapi-people)
                                      (leihs-lower-email-addresses leihs-users))]
    (->> zapi-people
         (filter #(get-zapi-field % [:business_contact :email_main]))
         (filter #(to-be-added-email-addresses 
                    (clojure.string/lower-case 
                      (get-zapi-field % [:business_contact :email_main])))))))

(defn- to-be-added-zapi-users-by-org-id [zapi-people leihs-users]
  (let [zapi-org-ids (->> zapi-people (map :id) (filter identity) str set)
        leihs-org-ids (->> leihs-users (map :org_id) (filter identity) set)
        to-be-added-org-ids (clojure.set/difference 
                              zapi-org-ids leihs-org-ids)]
    (def ^:dynamic zapi-org-ids* zapi-org-ids)
    (def ^:dynamic leihs-org-ids* leihs-org-ids)
    (def ^:dynamic to-be-added-org-ids* to-be-added-org-ids)
    (->> zapi-people
         (filter #(to-be-added-org-ids (:id %))))))


(defn- add-users [conf to-be-added-zapi-users]
  (let [show-progress (:progress conf)
        users (->> to-be-added-zapi-users
                   (map zapi->leihs-attributes))
        total-count (count users)]
    (loop [users users
           bar (progrock/progress-bar total-count)]
      (if-let [user (first users)]
        (do 
          (when show-progress (progrock/print bar) (flush))
          (if (:dry-run conf)
            (Thread/sleep 50)
            (leihs-api/add-user user conf))
          (recur (rest users) (progrock/tick bar)))
        (do
          (when show-progress
            (progrock/print (assoc bar 
                                   :done? true 
                                   :total  total-count
                                   :progress total-count))
            (flush)))))
    total-count))



(defn add-new-leihs-users [conf zapi-people leihs-users]
  (logging/info ">>> Adding new users to leihs >>>")
  (let [to-be-added-zapi-users (case (:leihs-sync-id conf)
                                 "email" (to-be-added-zapi-users-by-email zapi-people leihs-users)
                                 "org_id" (to-be-added-zapi-users-by-org-id zapi-people leihs-users))
        total-count (count to-be-added-zapi-users)
        show-progress (:progress conf)]
    (add-users conf to-be-added-zapi-users)
    (logging/info "<<< Added " total-count " users <<<")))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
(debug/debug-ns *ns*)
