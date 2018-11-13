(ns leihs.zhdk-sync.groups.update
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.utils.core :refer [presence str keyword]]
    [leihs.zhdk-sync.leihs-admin-api :as leihs-api]
    [leihs.zhdk-sync.groups.shared :refer :all]

    [cheshire.core :as cheshire]
    [progrock.core :as progrock]
    [clojure.java.io :as io]

    [timothypratley.patchin :refer [diff]]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I>]]
    [logbug.thrown :as thrown]
    ))


;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;;

(defn needs-update? [zapi-attrs leihs-attrs]
  (and leihs-attrs
       (let [id (:id leihs-attrs)
             leihs-attrs (dissoc leihs-attrs :id)]
         (if (not= leihs-attrs zapi-attrs)
           (do (logging/info "To be updated " id ": "
                             (diff leihs-attrs zapi-attrs))
               true)
           false))))

(defn to-be-updated-groups-by-org-id [zapi-groups leihs-groups]
  (let [leihs-groups-org-id-map (->> leihs-groups
                                    (filter :org_id)
                                    (map (fn [u] [(:org_id u)
                                                  (select-keys u attribute-keys)]))
                                    (into {}))]
    (->> zapi-groups
         (map zapi->leihs-attributes)
         (filter :org_id)
         (filter (fn [zapi-attrs]
                   (let [leihs-attrs (get leihs-groups-org-id-map (:org_id zapi-attrs))]
                     (needs-update? zapi-attrs leihs-attrs))))
         (map #(assoc % :id (-> leihs-groups-org-id-map
                                (get (-> % :org_id))
                                :id))))))



(defn update-existing-leihs-groups [conf zapi-groups leihs-groups]
  (logging/info ">>> Updating leihs groups >>>")
  (let [show-progress (:progress conf)
        to-be-updated-groups (to-be-updated-groups-by-org-id zapi-groups leihs-groups) 
        total-count (count to-be-updated-groups)]
    (def ^:dynamic *to-be-updated-groups* to-be-updated-groups)
    (loop [groups to-be-updated-groups
           updated-groups []
           bar (progrock/progress-bar total-count)]
      (if-let [group (first groups)]
        (do (when show-progress (progrock/print bar) (flush))
            (let [updated-group
                  (if (:dry-run conf)
                    (do (Thread/sleep 50) {})
                    (:body (leihs-api/update-group group conf)))]
              (recur (rest groups) 
                     (conj updated-groups updated-group)
                     (progrock/tick bar))))
        (do
          (when show-progress
            (progrock/print (assoc bar
                                   :done? true
                                   :total  total-count
                                   :progress total-count))
            (flush))
          (logging/info "<<< Updated " total-count " leihs groups <<<")
          updated-groups)))))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
