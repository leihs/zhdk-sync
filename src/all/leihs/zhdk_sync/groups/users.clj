(ns leihs.zhdk-sync.groups.users
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

(defn update-group-users [options group]
  (let [id (-> group :id presence)
        base-url (-> options :leihs-http-url presence)
        token (-> options :leihs-token presence) ]
    (assert id)
    (assert base-url)
    (assert token)
    (let [res (http-client/put
                (str base-url "/admin/groups/" id "/users/")
                {:accept :json
                 :content-type :json
                 :as :json
                 :basic-auth [token ""]
                 :body (cheshire/generate-string 
                         {:org_ids (:users group)})})]
      (or (< 0 (-> res :body :removed-user-ids count))
          (< 0 (-> res :body :added-user-ids count))))))

(defn update-groups-users [options zapi-groups-with-users]
  (let [show-progress (:progress options)
        groups-with-users (map (fn [[_ g]] g) zapi-groups-with-users)
        total-count (count groups-with-users)]
    (logging/info ">>> updating users of " total-count " groups >>>")
    (let [count-updated (loop [groups-with-users groups-with-users
                               bar (progrock/progress-bar total-count)
                               count-updated 0]
                          (if-let [group (first groups-with-users)]
                            (do
                              (when show-progress (progrock/print bar) (flush))
                              (let [was-updated? 
                                    (if (:dry-run options)
                                      (Thread/sleep 50)
                                      (update-group-users options group))]
                                (recur (rest groups-with-users) 
                                       (progrock/tick bar)
                                       (if was-updated? (inc count-updated) count-updated))))
                            (do
                              (when show-progress
                                (progrock/print (assoc bar
                                                       :done? true
                                                       :total  total-count
                                                       :progress total-count))
                                (flush))
                              count-updated)))]
      (logging/info "<<< updated users of " count-updated " groups <<<"))))
