(ns leihs.zhdk-sync.zapi-tests
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.utils.core :refer [presence str keyword]]
    [clojure.edn :as edn]

    [clojure.test :refer :all]
    [leihs.zhdk-sync.zapi :refer :all]
    [clojure.java.io :as io]
    ))

(defn zapi-token []
  (or (some-> "ZAPI_TOKEN" 
              System/getenv 
              presence)
      (some-> "secrets.clj" 
              io/resource
              slurp
              edn/read-string     
              :zapi-token
              )))

(deftest single-user-fetch
  (is (seq (people
             {:zapi-token (zapi-token)
              :disable-count-sanity-check true}
             {:last_name "schank"})))
  (is (thrown? java.lang.AssertionError
               (people
                 {:zapi-token (zapi-token)}
                 {:last_name "schank"}))))

(deftest fetch-all
  (is (seq (people
             {:zapi-token (zapi-token)}
             {}))))
