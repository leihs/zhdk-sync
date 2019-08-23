(ns leihs.zhdk-sync.users.shared
  (:refer-clojure :exclude [str keyword])
  (:require
    [leihs.utils.core :refer [presence str keyword]]
    [leihs.zhdk-sync.leihs-admin-api :as leihs-api]

    [cheshire.core :as cheshire]
    [progrock.core :as progrock]
    [clojure.java.io :as io]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I>]]
    [logbug.thrown :as thrown]
    ))


(defn get-zapi-field [zapi-person ks]
  (some-> zapi-person (get-in ks) presence))

(def attribute-keys
  [:address
   :badge_id
   :city
   :country
   :email
   :firstname
   :id ;; we need the id to compute the patch address
   :img256_url
   :img32_url
   :img_digest
   :lastname
   :login
   :org_id
   :phone
   :url
   :zip])

(def de-iso-codes
  (-> "iso-countries/langs/de.json"
      clojure.java.io/resource
      slurp
      cheshire/parse-string
      (get "countries")))


(defn zapi-lower-email-addresses [zapi-people]
  (->> zapi-people
       (map #(get-in % [:business_contact :email_main]))
       (filter identity)
       (map clojure.string/lower-case)
       set))

(defn leihs-lower-email-addresses [leihs-users]
  (->> leihs-users
       (map :email)
       (filter identity)
       (map clojure.string/lower-case)
       set))

(defn zapi->leihs-attributes [zapi-person]
  (let [evento-id (:id zapi-person)
        country-code (get-zapi-field zapi-person [:personal_contact :country_code])]
    {:address (->> [:address1 :address2]
                   (map #(some-> zapi-person
                                 (get-in [:personal_contact %])
                                 presence))
                   (filter identity)
                   (clojure.string/join ", "))
     :badge_id (or (get-zapi-field zapi-person [:leihs_temp :library_user_id])
                   (some->
                     (get-zapi-field zapi-person [:leihs_temp :matriculation_number])
                     (clojure.string/replace #"-" "")))
     :city (get-zapi-field zapi-person [:personal_contact :city])
     :country (when country-code (get de-iso-codes country-code))
     :email (get-zapi-field zapi-person [:business_contact :email_main])
     :firstname (get-zapi-field zapi-person [:basic :first_name])
     :img256_url (get-zapi-field zapi-person [:photos_badge :photos 0 :resource_link :links :self])
     :img32_url (get-zapi-field zapi-person [:photos_badge :photos 0 :resource_link :links :self])
     :img_digest (get-zapi-field zapi-person [:photos_badge :photos 0 :content_hash_sha1])
     :lastname (get-zapi-field zapi-person [:basic :last_name])
     :login (get-zapi-field zapi-person [:account :user_name])
     :org_id (str evento-id)
     :phone (or (get-zapi-field zapi-person [:business_contact :phone_business])
                (get-zapi-field zapi-person [:personal_contact :phone_business])
                (get-zapi-field zapi-person [:personal_contact :phone_mobile])
                (get-zapi-field zapi-person [:personal_contact :phone_private])
                (some-> zapi-person
                        (get-in [:personal_contact :phone_organizational])
                        first presence))
     :url (get-zapi-field zapi-person [:basic :url])
     :zip (->> [country-code
                (get-zapi-field zapi-person [:personal_contact :zip])]
               (map presence)
               (filter identity)
               (clojure.string/join "-"))}))

