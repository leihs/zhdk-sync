(ns leihs.zhdk-sync.zapi
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.utils.core :refer [presence str keyword]]
    [leihs.zhdk-sync.utils :refer [deviates-by-more-than-tenth?]]

    [clj-http.client :as http-client]
    [progrock.core :as progrock]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I>]]
    [logbug.thrown :as thrown]
    ))

;;; person/people ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(def person-fieldsets
  (->> ["account"
        "basic"
        "business_contact"
        "leihs_temp"
        "personal_contact"
        "photo"
        "photos_badge"
        "user_group"]
       (clojure.string/join "," )))

(def default-person-query-params
  {:fieldsets person-fieldsets
   :only_zhdk true
   :limit 100})

(defonce people* (atom nil))

;(reset! people* (take 10 @people*)
;(-> @people* first :user_group)

(defn- fetch-people [token limit estimate-count show-progress]
  (loop [data []
         page 0
         bar (progrock/progress-bar estimate-count)]
    (let [query-params (assoc default-person-query-params 
                              :offset (* page limit)
                              ; only for prototyping or debugging: 
                              ; :last_name "schank"
                              ; :last_name "kmit"
                              )]
      (when show-progress
        (progrock/print bar) (flush))
      (if-let [more-data (seq (-> (http-client/get
                                    "https://zapi.zhdk.ch/v1/person/"
                                    {:query-params query-params
                                     :accept :json
                                     :as :json
                                     :basic-auth [token ""]})
                                  :body :data))]
        (recur (concat data more-data)
               (inc page)
               (progrock/tick bar (count more-data)))
        (do
          (when show-progress
            (progrock/print (assoc bar
                                   :done? true
                                   :total (count data)
                                   :progress (count data)))
            (flush))
          data)))))

(defn- _people [options]
  (logging/info "Fetching people from ZAPI")
  (let [token (or (-> options :zapi-token presence)
                  (throw (IllegalStateException. "options :zapi-token is not set")))
        estimate-count (-> options :zapi-estimate-people-count)
        limit (-> options :zapi-page-limit)
        people (fetch-people token limit estimate-count (:progress options))]
    (when (and (-> options :skip-count-checks not) 
               (deviates-by-more-than-tenth? (count people) estimate-count))
      (throw (ex-info "ZAPI expected people count deviates by more than a tenth."
                      {:people-count (count people)
                       :estimate-count estimate-count})))
    (reset! people* people)
    people))

;(defonce people (memoize _people))
(def people _people)


;;; user-group/user-group ;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn- fetch-user-groups [token limit estimate-count show-progress]
  (loop [data []
         page 0
         bar (progrock/progress-bar estimate-count)]
    (let [query-params {:limit limit :offset (* page limit)}]
      (when show-progress
        (progrock/print bar) (flush))
      (if-let [more-data (seq (-> (http-client/get
                                    "https://zapi.zhdk.ch/v1/user-group/"
                                    {:query-params query-params
                                     :accept :json
                                     :as :json
                                     :basic-auth [token ""]})
                                  :body :data))]
        (recur (concat data more-data)
               (inc page)
               (progrock/tick bar (count more-data)))
        (do
          (when show-progress
            (progrock/print (assoc bar
                                   :done? true
                                   :total (count data)
                                   :progress (count data)))
            (flush))
          data)))))


;(fetch-user-groups (System/getenv "ZAPI_TOKEN") 100 1581 false)

(defonce user-groups* (atom nil))

;(first @user-groups*)

(defn- _user-groups [options]
  (logging/info "Fetching user-groups from ZAPI")
  (let [token (or (-> options :zapi-token presence)
                  (throw (IllegalStateException. "options :zapi-token is not set")))
        estimate-count (-> options :zapi-estimate-user-groups-count)
        limit (-> options :zapi-page-limit)
        user-groups (fetch-user-groups token limit estimate-count (:progress options))]
    (when (and (-> options :skip-count-checks not) 
               (deviates-by-more-than-tenth? (count user-groups) estimate-count))
      (throw (ex-info "ZAPI expected user-groups count deviates by more than a tenth."
                      {:user-groups-count (count user-groups)
                       :estimate-count estimate-count})))
    (reset! user-groups* user-groups)
    user-groups))

;(_user-groups {:zapi-token (System/getenv "ZAPI_TOKEN") :zapi-page-limit 100 :zapi-estimate-user-groups-count 1500 :progess false})

(defonce user-groups (memoize _user-groups))
(def user-groups _user-groups)

;;; compute groups with users (we have users withs groups) ;;;;;;;;;;;;;;;;;;;;;

(defn person-group-ids [person]
  (->> person
       :user_group
       :id
       (map :id)))

;(-> @people* first person-group-ids)

(defn map-user-groups [user-groups]
  (->> user-groups 
       (map (fn [ug] [(:id ug) ug]))
       (into {})))

;(map-user-groups @user-groups*)

(defn add-user-to-group [group-id user-id groups-map]
  (update-in groups-map [group-id :users]
             (fn [users]
               (conj (or users #{}) user-id))))

(defn add-person-to-groups
  [groups-map person]
  (let [user-id (:id person)]
    (reduce (fn [groups-map group-id]
              (add-user-to-group group-id user-id groups-map))
            groups-map
            (person-group-ids person))))

(defn user-groups-with-users [user-groups people]
  (reduce add-person-to-groups
          (map-user-groups user-groups)
          people))

;(user-groups-with-users (take 100 @user-groups*) (take 10 @people*))
;(user-groups-with-users @user-groups* @people*)

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
