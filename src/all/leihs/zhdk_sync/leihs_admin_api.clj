(ns leihs.zhdk-sync.leihs-admin-api
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.utils.core :refer [presence str keyword]]
    [leihs.zhdk-sync.utils :refer [deviates-by-more-than-tenth?]]

    [cheshire.core :as cheshire]
    [clj-http.client :as http-client]
    [progrock.core :as progrock]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I>]]
    [logbug.thrown :as thrown]
    ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; users ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def user-fields
  #{
    :account_enabled
    :address
    :badge_id
    :city
    :country
    :created_at
    :email
    :firstname
    :id
    :img256_url
    :img32_url
    :img_digest
    :is_admin
    :lastname
    :login
    :org_id
    :password_sign_in_enabled
    :phone
    :updated_at
    :url
    :zip
    })

(defn- fetch-users [base-url token estimate-count show-progress]
  (loop [page 1
         users []
         bar (progrock/progress-bar estimate-count)]
    (when show-progress (progrock/print bar) (flush))
    (if-let [more-users (seq (-> (http-client/get
                                   (str base-url "/admin/users/")
                                   {:query-params
                                    {:page page
                                     :per-page 1000
                                     :fields (cheshire/generate-string user-fields)}
                                    :accept :json
                                    :as :json
                                    :basic-auth [token ""]})
                                 :body :users))]
      (recur (inc page)
             (concat users more-users)
             (progrock/tick bar (count more-users)))
      (do (when show-progress
            (progrock/print (assoc bar
                                   :done? true
                                   :total (count users)
                                   :progress (count users)))
            (flush))
          users))))

(defn- _users [options]
  (logging/info "Fetching existing USERS from leihs")
  (let [base-url (-> options :leihs-http-url presence)
        token (-> options :leihs-token presence)
        estimate-count (-> options :leihs-estimate-user-count)]
    (assert base-url)
    (assert token) 
    (def ^:dynamic *users* (fetch-users base-url token estimate-count (:progress options)))
    *users*))

(defonce users (memoize _users))

(defn add-user [user-attributes conf]
  (let [base-url (-> conf :leihs-http-url presence)
        token (-> conf :leihs-token presence) ]
    (assert base-url)
    (assert token)
    (-> (http-client/post
          (str base-url "/admin/users/")
          {:accept :json
           :content-type :json
           :as :json
           :basic-auth [token ""]
           :body (cheshire/generate-string user-attributes)
           }))))

(defn update-user [user-attributes conf]
  (let [id (-> user-attributes :id presence)
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
           :body (cheshire/generate-string user-attributes)
           }))))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;; groups ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;
;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- fetch-groups [base-url token estimate-count show-progress]
  (loop [page 1
         groups []
         bar (progrock/progress-bar estimate-count)]
    (when show-progress (progrock/print bar) (flush))
    (if-let [more-groups (seq (-> (http-client/get
                                    (str base-url "/admin/groups/")
                                    {:query-params
                                     {:page page
                                      :per-page 1000}
                                     :accept :json
                                     :as :json
                                     :basic-auth [token ""]})
                                  :body :groups))]
      (recur (inc page)
             (concat groups more-groups)
             (progrock/tick bar (count more-groups)))
      (do (when show-progress
            (progrock/print (assoc bar
                                   :done? true
                                   :total (count groups)
                                   :progress (count groups)))
            (flush))
          groups))))

;(fetch-groups "http://localhost:3211" "secret" 1 false)

(defn groups [options]
  (logging/info "Fetching existing GROUPS from leihs")
  (let [base-url (-> options :leihs-http-url presence)
        token (-> options :leihs-token presence)
        estimate-count (-> options :leihs-estimate-group-count)
        show-progress (:progress options)]
    (assert base-url)
    (assert token)
    (def ^:dynamic *groups* (fetch-groups base-url token estimate-count show-progress))
    *groups*))

(defn add-group [group-attributes conf]
  (let [base-url (-> conf :leihs-http-url presence)
        token (-> conf :leihs-token presence) ]
    (assert base-url)
    (assert token)
    (-> (http-client/post
          (str base-url "/admin/groups/")
          {:accept :json
           :content-type :json
           :as :json
           :basic-auth [token ""]
           :body (cheshire/generate-string group-attributes)
           }))))

(defn update-group [group-attributes conf]
  (let [id (-> group-attributes :id presence)
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
           :body (cheshire/generate-string group-attributes)
           }))))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)

