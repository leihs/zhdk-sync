(ns leihs.zhdk-sync.users.add
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.utils.core :refer [presence str keyword]]
    [leihs.zhdk-sync.leihs-admin-api :as leihs-api]
    [leihs.zhdk-sync.users.shared :refer :all]
    [leihs.zhdk-sync.users.photo :as photo]

    [cheshire.core :as cheshire]
    [progrock.core :as progrock]
    [clojure.java.io :as io]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I>]]
    [logbug.thrown :as thrown]
    ))

(def add-defaults
  {:password_sign_in_enabled false
   :system_admin_protected true})

(defn- to-be-added-zapi-users-by-org-id [zapi-people leihs-users]
  (let [zapi-org-ids (->> zapi-people (map :id) (filter identity) (map str) set)
        leihs-org-ids (->> leihs-users (map :org_id) (filter identity) set)
        to-be-added-org-ids (clojure.set/difference
                              zapi-org-ids leihs-org-ids)]
    (def ^:dynamic zapi-org-ids* zapi-org-ids)
    (def ^:dynamic leihs-org-ids* leihs-org-ids)
    (def ^:dynamic to-be-added-org-ids* to-be-added-org-ids)
    (->> zapi-people
         (filter #(to-be-added-org-ids (-> % :id str))))))


(defn- add-users [conf to-be-added-zapi-users]
  (let [show-progress (:progress conf)
        to-be-added-users (->> to-be-added-zapi-users
                               (map zapi->leihs-attributes))
        total-count (count to-be-added-users)]
    (loop [to-be-added-users to-be-added-users
           added-users []
           bar (progrock/progress-bar total-count)]
      (if-let [user (first to-be-added-users)]
        (do (when show-progress (progrock/print bar) (flush))
            (let [added-user
                  (if (:dry-run conf)
                    (do (Thread/sleep 50) {})
                    (->> user
                         (merge add-defaults)
                         (photo/update-images conf)
                         (leihs-api/add-user conf)
                         :body))]
              (recur (rest to-be-added-users) 
                     (conj added-users added-user)
                     (progrock/tick bar))))
        (do (when show-progress
              (progrock/print (assoc bar
                                     :done? true
                                     :total  total-count
                                     :progress total-count))
              (flush))
            added-users)))))



(defn add-new-leihs-users [conf zapi-people leihs-users]
  "Returns a seq of maps each representing one added users."
  (logging/info ">>> Adding new users to leihs >>>")
  (let [to-be-added-zapi-users (to-be-added-zapi-users-by-org-id zapi-people leihs-users)
        show-progress (:progress conf)
        added-users (add-users conf to-be-added-zapi-users)]
    (logging/info "<<< Added " (count added-users) " users <<<")
    added-users))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
