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


(def fieldsets 
  (clojure.string/join 
    "," [
         "basic"
         "business_contact"
         "leihs_temp"
         "personal_contact"
         "photo"
         "user_group"
         ]))

(def default-limit 100)

(def default-query-params
  {:fieldsets fieldsets
   :only_zhdk true
   :limit default-limit})

(defn sanitiy-check-email-address-unqiueness! [people]
  (let [count-people (count people)
        count-email-addresses (->> people 
                                   (map #(-> % :business_contact :email_main)) 
                                   (map clojure.string/lower-case)
                                   (map presence)
                                   (filter identity)
                                   set
                                   count)]
    (when-not (= count-people count-email-addresses)
      (throw (ex-info "Each person must have an existing and unique email-address."
                      {:count-people count-people
                       :count-email-addresses count-email-addresses})))))


(defn filter-zapi-person [person]
  (if-not (-> person
              :business_contact
              :email_main
              presence)
    (do (logging/warn "Dropping person " person 
                      " because business email is not present.")
        false)
    true))

(def people* (atom nil))

(defn- fetch-people [token query-params estimate-count show-progress]
  (loop [data []
         page 0
         bar (progrock/progress-bar estimate-count)]
    (let [limit (:limit query-params)
          query-params (assoc query-params :offset (* page limit))]
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
          (->> data (filter filter-zapi-person)))))))

(defn- _people [options query-params]
  (logging/info "Fetching people from ZAPI")
  (let [token (or (-> options :zapi-token presence)
                  (throw (IllegalStateException. "options :zapi-token is not set")))
        query-params (merge default-query-params query-params)
        estimate-count (-> options :zapi-estimate-people-count)
        people (fetch-people token query-params estimate-count (:progress options))]
    (when (deviates-by-more-than-tenth? (count people) estimate-count)
      (throw (ex-info "ZAPI expected people count deviates by more than a tenth."
                      {:people-count (count people) 
                       :estimate-count estimate-count})))
    (sanitiy-check-email-address-unqiueness! people)
    (reset! people* people)
    people))


(defonce people (memoize _people))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
