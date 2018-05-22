(ns leihs.zhdk-sync.sync.add
  (:refer-clojure :exclude [str keyword])
  (:require 
    [leihs.utils.core :refer [presence str keyword]]
    [leihs.zhdk-sync.leihs-admin-api :as leihs-api]
    [leihs.zhdk-sync.sync.shared :refer :all]

    [cheshire.core :as cheshire]
    [progrock.core :as progrock]
    [clojure.java.io :as io]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I>]]
    [logbug.thrown :as thrown]
    ))


(defn add-new-leihs-users [conf zapi-people leihs-users]
  (logging/info ">>> Adding new users to leihs >>>")
  (let [show-progress (:progress conf)
        to-be-added-email-addresses (clojure.set/difference 
                                      (zapi-lower-email-addresses zapi-people)
                                      (leihs-lower-email-addresses leihs-users))
        add-users (->> zapi-people
                       (filter #(to-be-added-email-addresses 
                                  (clojure.string/lower-case 
                                    (get-zapi-field % [:business_contact :email_main]))))
                       (map zapi->leihs-attributes)
                       seq)
        total-count (count add-users)]
    (def ^:dynamic *to-be-added-email-addresses* to-be-added-email-addresses)
    (def ^:dynamic *add-users* add-users)
    ;(logging/info total-count)
    (loop [users add-users
           bar (progrock/progress-bar total-count)]
      (if-let [user (first users)]
        (do 
          (when show-progress (progrock/print bar) (flush))
          (if (:dry-run conf)
            (Thread/sleep 50)
            (leihs-api/add-user user conf))
          (recur (rest users) (progrock/tick bar)))
        (do
          (when show-progress
            (progrock/print (assoc bar 
                                   :done? true 
                                   :total  total-count
                                   :progress total-count))
            (flush)))))
    (logging/info "<<< Added " total-count " users <<<")
    add-users))



;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
