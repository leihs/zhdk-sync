(ns leihs.zhdk-sync.groups.add
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.utils.core :refer [presence str keyword]]
    [leihs.zhdk-sync.leihs-admin-api :as leihs-api]
    [leihs.zhdk-sync.groups.shared :refer :all]

    [cheshire.core :as cheshire]
    [progrock.core :as progrock]
    [clojure.java.io :as io]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I>]]
    [logbug.thrown :as thrown]
    ))


(defn- to-be-added-zapi-groups-by-org-id [zapi-groups leihs-groups]
  (def ^:dynamic *zapi-groups* zapi-groups)
  (let [zapi-org-ids (->> zapi-groups (map :id) (filter identity) (map str) set)
        leihs-org-ids (->> leihs-groups (map :org_id) (filter identity) set)
        to-be-added-org-ids (clojure.set/difference
                              zapi-org-ids leihs-org-ids)]
    (def ^:dynamic *zapi-org-ids* zapi-org-ids)
    (def ^:dynamic *leihs-org-ids* leihs-org-ids)
    (def ^:dynamic *to-be-added-org-ids* to-be-added-org-ids)
    (->> zapi-groups
         (filter #(to-be-added-org-ids (-> % :id str))))))


(defn- add-groups [conf to-be-added-zapi-groups]
  (let [show-progress (:progress conf)
        groups (->> to-be-added-zapi-groups
                    (map zapi->leihs-attributes))
        total-count (count groups)]
    (loop [groups groups
           added-groups []
           bar (progrock/progress-bar total-count)]
      (if-let [group (first groups)]
        (do (when show-progress (progrock/print bar) (flush))
            (let [added-group
                  (if (:dry-run conf)
                    (do (Thread/sleep 50) {})
                    (-> (leihs-api/add-group group conf) :body))]
              (recur (rest groups) 
                     (conj added-groups added-group)
                     (progrock/tick bar))))
        (do (when show-progress
              (progrock/print (assoc bar
                                     :done? true
                                     :total  total-count
                                     :progress total-count))
              (flush))
            added-groups)))))


(defn add-new-leihs-groups [conf zapi-groups leihs-groups]
  (logging/info ">>> Adding new groups to leihs >>>")
  (let [to-be-added-zapi-groups (to-be-added-zapi-groups-by-org-id zapi-groups leihs-groups)
        show-progress (:progress conf)
        added-groups (add-groups conf to-be-added-zapi-groups)]
    (logging/info "<<< Added " (count added-groups)" groups <<<")
    added-groups))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/re-apply-last-argument #' add-new-leihs-groups)
;(debug/debug-ns *ns*)
