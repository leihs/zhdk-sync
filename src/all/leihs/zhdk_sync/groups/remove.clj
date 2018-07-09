(ns leihs.zhdk-sync.groups.remove
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.utils.core :refer [presence str keyword]]
    [leihs.zhdk-sync.leihs-admin-api :as leihs-api]
    [leihs.zhdk-sync.groups.shared :refer :all]

    [clj-http.client :as http-client]
    [cheshire.core :as cheshire]
    [progrock.core :as progrock]
    [clojure.java.io :as io]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I>]]
    [logbug.thrown :as thrown]
    ))

(defn remove-group [group conf]
  (let [id (-> group :id presence)
        base-url (-> conf :leihs-http-url presence)
        token (-> conf :leihs-token presence) ]
    (assert id)
    (assert base-url)
    (assert token)
    (if (:dry-run conf)
      (do (Thread/sleep 50)
          true)
      (http-client/delete
        (str base-url "/admin/groups/" id)
        {:accept :json
         :as :json
         :basic-auth [token ""]}))))

(defn disable-group [group conf]
  (let [id (-> group :id presence)
        base-url (-> conf :leihs-http-url presence)
        token (-> conf :leihs-token presence)]
    (assert id)
    (assert base-url)
    (assert token)
    (-> (http-client/patch
          (str base-url "/admin/groups/" id)
          {:accept :json
           :content-type :json
           :as :json
           :basic-auth [token ""]
           :body (cheshire/generate-string {:org_id nil
                                            :email nil
                                            :account_enabled false
                                            :password_sign_in_enabled false})}))))

(defn to-be-removed-leihs-groups-by-org-id [zapi-groups leihs-groups]
  (let [zapi-org-ids(->> zapi-groups
                              (map :id)
                              (filter identity)
                              (map str)
                              set)]
    (->> leihs-groups
         (filter #(-> % :org_id presence))
         (filter #(not (zapi-org-ids
                         (some-> % :org_id)))))))


(defn remove-or-disable [conf zapi-groups leihs-groups]
  (logging/info ">>> Removing or disabling removed groups >>>")
  (let [show-progress (:progress conf)
        to-be-removed-leihs-groups (to-be-removed-leihs-groups-by-org-id zapi-groups leihs-groups)
        total-count (count to-be-removed-leihs-groups)]
    (def ^:dynamic *to-be-removed-leihs-groups* to-be-removed-leihs-groups)
    (loop [groups to-be-removed-leihs-groups
           removed-groups []
           bar (progrock/progress-bar total-count)]
      (when show-progress (progrock/print bar) (flush))
      (if-let [group (first groups)]
        (do (remove-group group conf)
            (recur (rest groups)
                   (conj removed-groups group)
                   (progrock/tick bar 1)))
        (do (when show-progress
              (progrock/print (assoc bar
                                     :done? true
                                     :total (count groups)
                                     :progress (count groups)))
              (flush))
            (logging/info (str "<<< Removed " (count removed-groups) " groups <<<")))))))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)

