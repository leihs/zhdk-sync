(ns leihs.zhdk-sync.main
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.utils.core :refer [presence str keyword]]
    [leihs.zhdk-sync.zapi :as zapi]
    [leihs.zhdk-sync.leihs-admin-api :as leihs-admin-api]
    [leihs.zhdk-sync.users.add :as user-sync-add]
    [leihs.zhdk-sync.users.update :as user-sync-update]
    [leihs.zhdk-sync.users.remove :as user-sync-remove]
    [leihs.zhdk-sync.groups.add :as group-sync-add]
    [leihs.zhdk-sync.groups.update :as group-sync-update]
    [leihs.zhdk-sync.groups.remove :as group-sync-remove]
    [leihs.zhdk-sync.groups.users :as group-sync-users]

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
  {:LEIHS_ESTIMATE_USER_COUNT 10500
   :LEIHS_ESTIMATE_GROUP_COUNT 1500
   :LEIHS_HTTP_URL "http://localhost:3211"
   :LEIHS_SYNC_ID "org_id"
   :ZAPI_ESTIMATE_PEOPLE_COUNT 4849
   :ZAPI_ESTIMATE_USER_GROUPS_COUNT 1581
   :ZAPI_PAGE_LIMIT 100
   :ZAPI_HTTP_URL "https://zapi.zhdk.ch" })

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
   [nil "--leihs-estimate-group-count LEIHS_ESTIMATE_GROUP_COUNT"
    "Estimate of the group count before the sync, abort if deviation > 10%"
    :default (env-or-default :LEIHS_ESTIMATE_GROUP_COUNT :parse-fn parse-int)
    :parse-fn parse-int]
   ["-k" "--leihs-skip-count-check"
    "Do not abort even if the deviation is > 10%; useful for intial sync."
    :default false]
   [nil "--leihs-token LEIHS_TOKEN"
    :default (env-or-default :LEIHS_TOKEN)
    :parse-fn identity]
   [nil "--leihs-sync-id LEIHS_SYNC_ID"
    "The attribute by which the the user is identified, either org_id or email."
    :default (env-or-default :LEIHS_SYNC_ID)
    :parse-fn identity]
   ["-p" "--progress" "Show progess bar and estimated time to finish"
    :default false]
   [nil "--zapi-token ZAPI_TOKEN"
    :default (env-or-default :ZAPI_TOKEN)
    :parse-fn identity]
   [nil "--zapi-page-limit ZAPI_PAGE_LIMIT"
    :default (env-or-default :ZAPI_PAGE_LIMIT)
    :parse-fn identity]
   [nil "--zapi-estimate-user-groups-count ZAPI_ESTIMATE_USER_GROUPS_COUNT"
    "Estimate of the user-groups count, abort if deviation > 10%"
    :default (env-or-default :ZAPI_ESTIMATE_USER_GROUPS_COUNT :parse-fn parse-int)
    :parse-fn parse-int]
   [nil "--zapi-estimate-people-count ZAPI_ESTIMATE_PEOPLE_COUNT"
    "Estimate of the people count, abort if deviation > 10%"
    :default (env-or-default :ZAPI_ESTIMATE_PEOPLE_COUNT :parse-fn parse-int)
    :parse-fn parse-int]])

(defn run [options]
  (catcher/with-logging
    {}
    (let [zapi-people (zapi/people options)
          leihs-users (leihs-admin-api/users options)]
      (user-sync-add/add-new-leihs-users options zapi-people leihs-users)
      (user-sync-update/update-existing-leihs-users options zapi-people leihs-users)
      (user-sync-remove/remove-or-disable options zapi-people leihs-users)
      (let [zapi-groups (zapi/user-groups options)
            zapi-groups-with-users (zapi/user-groups-with-users zapi-groups zapi-people)
            leihs-groups (leihs-admin-api/groups options)]
        (group-sync-add/add-new-leihs-groups options zapi-groups leihs-groups)
        (group-sync-update/update-existing-leihs-groups options zapi-groups leihs-groups)
        (group-sync-remove/remove-or-disable options zapi-groups leihs-groups)
        (group-sync-users/update-groups-users options zapi-groups-with-users)
        ))))

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

;(-main "-k")


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
