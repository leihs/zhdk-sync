(ns leihs.zhdk-sync.prtg
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.utils.core :refer [presence str keyword]]

    [cheshire.core :as cheshire]
    [clj-http.client :as http-client]

    [clojure.tools.logging :as logging]
    ))


(defn post [prtg-url msg]
  (logging/info 'prtg-msg msg)
  (http-client/post
    prtg-url
    {:accept :json
     :content-type :json
     :as :json
     :body
     (cheshire/generate-string msg)}))

(defn send-success [prtg-url data]
  (let [msg {:prtg
             {:result
              (->> [:added-users 
                    :updated-users 
                    :removed-users 
                    :disabled-users
                    :synced-users
                    :added-groups
                    :updated-groups
                    :removed-groups
                    :synced-groups
                    ]
                   (map (fn [kw]
                          {:channel kw
                           :unit "Count"
                           :value (->> (get data kw) count)})))}}]
    (post prtg-url msg)))

(defn send-error [prtg-url ex]
  (let [msg {:prtg
             {:error 1
              :text (str ex)}}]
    (post prtg-url msg)))

;(send-success nil {:updated-users [{:foo :bar}]})
