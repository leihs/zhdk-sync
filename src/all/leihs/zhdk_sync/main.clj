(ns leihs.zhdk-sync.main
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.utils.core :refer [presence str keyword]]

    [java-time]

    [leihs.zhdk-sync.groups.add :as group-sync-add]
    [leihs.zhdk-sync.groups.remove :as group-sync-remove]
    [leihs.zhdk-sync.groups.update :as group-sync-update]
    [leihs.zhdk-sync.groups.users :as group-sync-users]
    [leihs.zhdk-sync.leihs-admin-api :as leihs-admin-api]
    [leihs.zhdk-sync.prtg :as prtg]
    [leihs.zhdk-sync.users.add :as user-sync-add]
    [leihs.zhdk-sync.users.remove :as user-sync-remove]
    [leihs.zhdk-sync.users.update :as user-sync-update]
    [leihs.zhdk-sync.zapi :as zapi]

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
  {:LEIHS_ESTIMATE_USER_COUNT 10894
   :LEIHS_ESTIMATE_GROUP_COUNT 1650
   :LEIHS_HTTP_URL "http://localhost:3211"
   :ZAPI_ESTIMATE_PEOPLE_COUNT 4671 
   :ZAPI_ESTIMATE_USER_GROUPS_COUNT 1650
   :ZAPI_PAGE_LIMIT 100
   :ZAPI_HTTP_URL "https://zapi.zhdk.ch" 
   :PRTG_URL nil})

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
    "Estimate of the user count"
    :default (env-or-default :LEIHS_ESTIMATE_USER_COUNT :parse-fn parse-int)
    :parse-fn parse-int]
   [nil "--leihs-estimate-group-count LEIHS_ESTIMATE_GROUP_COUNT"
    "Estimate of the group count"
    :default (env-or-default :LEIHS_ESTIMATE_GROUP_COUNT :parse-fn parse-int)
    :parse-fn parse-int]
   ["-k" "--leihs-skip-count-check"
    "Do not abort even if the deviation is > 10%; useful for intial sync."
    :default false]
   [nil "--leihs-token LEIHS_TOKEN"
    :default (env-or-default :LEIHS_TOKEN)
    :parse-fn identity]
   ["-p" "--progress" "Show progess bar and estimated time to finish"
    :default false]
   [nil "--prtg-url PRTG_URL"
    (str "default: " (:PRTG_URL defaults))
    :default (env-or-default :PRTG_URL)
    :parse-fn identity]
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


(defn initial-sync? [users]
  (->> users 
       (filter #(re-matches #".*\|zhdk$" (or (:org_id %) ""))) 
       first boolean))

(defn run [options]
  (try 
    (let [zapi-people (zapi/people options)
          leihs-users (leihs-admin-api/users options)
          options (assoc options :leihs-sync-id
                         (if (initial-sync? leihs-users)
                           "email"
                           "org_id"))
          added-users (user-sync-add/add-new-leihs-users 
                        options zapi-people leihs-users)
          updated-users (user-sync-update/update-existing-leihs-users 
                          options zapi-people leihs-users)
          removed-or-disabled-users (user-sync-remove/remove-or-disable 
                                      options zapi-people leihs-users)]
      (let [zapi-groups (zapi/user-groups options)
            zapi-groups-with-users (zapi/user-groups-with-users zapi-groups zapi-people)
            leihs-groups (leihs-admin-api/groups options)
            added-groups (group-sync-add/add-new-leihs-groups options zapi-groups leihs-groups)
            updated-groups (group-sync-update/update-existing-leihs-groups options zapi-groups leihs-groups)
            removed-groups (group-sync-remove/remove-groups options zapi-groups leihs-groups)]
        (group-sync-users/update-groups-users options zapi-groups-with-users)
        (when-let [url (:prtg-url options)]
          (prtg/send-success url (merge {}
                                        removed-or-disabled-users
                                        {:added-users added-users
                                         :updated-users updated-users
                                         :synced-users zapi-people
                                         :added-groups added-groups
                                         :updated-groups updated-groups
                                         :removed-groups removed-groups
                                         :synced-groups zapi-groups
                                         })))))

    (catch Throwable t
      (logging/error t)
      (when-let [url (:prtg-url options)]
        (prtg/send-error url t)
        (System/exit -1)))))


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

;(-main "-k" "-d")


;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
