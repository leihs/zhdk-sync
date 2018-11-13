(ns leihs.zhdk-sync.users.remove
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.utils.core :refer [presence str keyword]]
    [leihs.zhdk-sync.leihs-admin-api :as leihs-api]
    [leihs.zhdk-sync.users.shared :refer :all]

    [clj-http.client :as http-client]
    [cheshire.core :as cheshire]
    [progrock.core :as progrock]
    [clojure.java.io :as io]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I>]]
    [logbug.thrown :as thrown]
    ))

(defn remove-user [user conf]
  (let [id (-> user :id presence)
        base-url (-> conf :leihs-http-url presence)
        token (-> conf :leihs-token presence) ]
    (assert id)
    (assert base-url)
    (assert token)
    (if (:dry-run conf)
      (do (Thread/sleep 50)
          true)
      (let [res (http-client/delete
                  (str base-url "/admin/users/" id)
                  {:accept :json
                   :as :json
                   :basic-auth [token ""]
                   :throw-exceptions false})]
        (case (:status res)
          409 false
          204 true
          (throw (ex-info "Unexpected delete user status" res)))))))

(defn disable-user [user conf]
  (let [id (-> user :id presence)
        base-url (-> conf :leihs-http-url presence)
        token (-> conf :leihs-token presence)]
    (assert id)
    (assert base-url)
    (assert token)
    (-> (http-client/patch
          (str base-url "/admin/users/" id)
          {:accept :json
           :content-type :json
           :as :json
           :basic-auth [token ""]
           :body (cheshire/generate-string {:org_id nil
                                            :email nil
                                            :account_enabled false
                                            :password_sign_in_enabled false})}))))

(defn to-be-removed-leihs-users-by-email [zapi-people leihs-users]
  (let [zapi-email-addresses (zapi-lower-email-addresses zapi-people)]
    (->> leihs-users
         (filter #(-> % :org_id presence))
         (filter #(not (zapi-email-addresses
                         (some-> % :email clojure.string/lower-case)))))))

(defn to-be-removed-leihs-users-by-org-id [zapi-people leihs-users]
  (let [zapi-org-ids(->> zapi-people
                              (map :id)
                              (filter identity)
                              (map str)
                              set)]
    (->> leihs-users
         (filter #(-> % :org_id presence))
         (filter #(not (zapi-org-ids
                         (some-> % :org_id)))))))


(defn remove-or-disable [conf zapi-people leihs-users]
  (logging/info ">>> Removing or disabling removed users >>>")
  (let [show-progress (:progress conf)
        to-be-removed-leihs-users (case (:leihs-sync-id conf)
                                    "email" (to-be-removed-leihs-users-by-email zapi-people leihs-users)
                                    "org_id" (to-be-removed-leihs-users-by-org-id zapi-people leihs-users))
        total-count (count to-be-removed-leihs-users)]
    (def ^:dynamic *to-be-removed-leihs-users* to-be-removed-leihs-users)
    (loop [users to-be-removed-leihs-users
           removed-users []
           disabled-users []
           bar (progrock/progress-bar total-count)]
      (when show-progress (progrock/print bar) (flush))
      (if-let [user (first users)]
        (if (remove-user user conf)
          (recur (rest users)
                 (conj removed-users user)
                 disabled-users
                 (progrock/tick bar 1))
          (do (disable-user user conf)
              (recur (rest users)
                     removed-users
                     (conj disabled-users user)
                     (progrock/tick bar 1))))
        (do (when show-progress
              (progrock/print (assoc bar
                                     :done? true
                                     :total (count users)
                                     :progress (count users)))
              (flush))
            (logging/info (str "<<< Removed " (count removed-users) " and disabled " (count disabled-users) " users <<<"))
            {:removed-users removed-users
             :disabled-users disabled-users})))))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)

