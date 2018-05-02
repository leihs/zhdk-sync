(ns leihs.zhdk-sync.main
  (:refer-clojure :exclude [str keyword])
  (:require 
    [leihs.utils.core :refer [presence str keyword]]
    [leihs.zhdk-sync.zapi :as zapi]
    [leihs.zhdk-sync.leihs-admin-api :as leihs-admin-api]
    [leihs.zhdk-sync.sync.add :as sync-add]
    [leihs.zhdk-sync.sync.update :as sync-update]
    [leihs.zhdk-sync.sync.remove :as sync-remove]

    [clojure.tools.cli :as cli :refer [parse-opts]]
    [clojure.pprint :refer [pprint]]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I>]]
    [logbug.thrown :as thrown]
    )
  (:gen-class)
  )


(thrown/reset-ns-filter-regex #"^(leihs)\..*")

(defn parse-int [s]
  (Long/parseLong s))

(def defaults 
  {:LEIHS_HTTP_URL "http://localhost:3211"
   :ZAPI_HTTP_URL "https://zapi.zhdk.ch"
   :LEIHS_ESTIMATE_USER_COUNT 10500
   :ZAPI_ESTIMATE_PEOPLE_COUNT 4357})

(defn env-or-default [kw & {:keys [parse-fn]
                            :or {parse-fn identity}}]
  (or (-> (System/getenv) (get (str kw) nil) presence)
      (get defaults kw nil)))


(def cli-options
  [["-d" "--dry-run" "Do not alter any data"
    :default false]
   ["-h" "--help"]
   ["-l" "--leihs-http-url LEIHS_HTTP_URL"
    (str "default: " (:LEIHS_HTTP_URL defaults))
    :default (env-or-default :LEIHS_HTTP_URL) 
    :parse-fn identity]
   [nil "--leihs-estimate-user-count LEIHS_ESTIMATE_USER_COUNT"
    "Estimate of the user count before the sync, abort if deviation > 10%"
    :default (env-or-default :LEIHS_ESTIMATE_USER_COUNT :parse-fn parse-int)
    :parse-fn parse-int]
   [nil "--leihs-skip-user-count-check"
    "Do not abort even if the deviation is > 10%"
    :default false]
   [nil "--leihs-token LEIHS_TOKEN"
    :default (env-or-default :LEIHS_TOKEN) 
    :parse-fn identity]
   ;["-z" "--zapi-http-url ZAPI_HTTP_URL"
   ; (str "default: " (:ZAPI_HTTP_URL defaults))
   ; :default (env-or-default :ZAPI_HTTP_URL) 
   ; :parse-fn identity]
   ["-p" "--progress" "Show progess bar and estimated time to finish"
    :default false]
   [nil "--zapi-token ZAPI_TOKEN"
    :default (env-or-default :ZAPI_TOKEN) 
    :parse-fn identity]
   [nil "--zapi-estimate-people-count ZAPI_ESTIMATE_PEOPLE_COUNT"
    "Estimate of the people count, abort if deviation > 10%"
    :default (env-or-default :ZAPI_ESTIMATE_PEOPLE_COUNT :parse-fn parse-int)
    :parse-fn parse-int]])

(defn run [options]
  (catcher/with-logging
    {}
    (let [zapi-people (zapi/people options {})
          leihs-users (leihs-admin-api/users options)]
      (sync-add/add-new-leihs-users options zapi-people leihs-users)
      (sync-update/update-existing-leihs-users options zapi-people leihs-users)
      (sync-remove/remove-or-disable options zapi-people leihs-users))))

(defn main-usage [options-summary & more]
  (->> ["Leihs ZHDK-Sync"
        ""
        "usage: leihs_zhdk-sync [<opts>] [<args>]"
        ""
        "Options:"
        options-summary
        ""
        ""
        (when more
          ["-------------------------------------------------------------------"
           (with-out-str (pprint more))
           "-------------------------------------------------------------------"])]
       flatten (clojure.string/join \newline)))

(defn -main [& args]
  (let [{:keys [options arguments errors summary]}
        (cli/parse-opts args cli-options :in-order true)
        pass-on-args (->> [options (rest arguments)]
                          flatten (into []))]
    (cond
      (:help options) (println (main-usage summary {:args args :options options}))
      :else (run options)
      )))


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
