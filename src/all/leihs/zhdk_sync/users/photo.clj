(ns leihs.zhdk-sync.users.photo
  (:refer-clojure :exclude [str keyword])
  (:require 
    [leihs.utils.core :refer [presence str keyword]]

    [clj-http.client :as http-client]
    [fivetonine.collage.util :as collage-util]
    [fivetonine.collage.core :as collage]

    [clojure.tools.logging :as logging]
    [logbug.catcher :as catcher]
    [logbug.debug :as debug :refer [I>]]
    [logbug.thrown :as thrown])
  
  (:import
    [java.awt.image BufferedImage]
    [java.io ByteArrayInputStream ByteArrayOutputStream]
    [java.util Base64]
    [javax.imageio ImageIO]
    
    [com.twelvemonkeys.imageio.plugins.jpeg JPEGImageReader]
    
    ))

(defn fetch-img [options user]
  (let [token (or (-> options :zapi-token presence)
                  (throw (IllegalStateException. "options :zapi-token is not set")))
        url (:img256_url user)]
    (try
      (-> (http-client/get
            url
            {:accept :json
             :as :json
             :basic-auth [token ""]})
          :body 
          :file_content_base64
          (.getBytes "UTF-8")
          (#(.decode (Base64/getDecoder) %))
          ByteArrayInputStream.
          ImageIO/read)
      (catch Exception e
        (throw (ex-info
                 "Image read error"
                 {:url url
                  :user user} e))))))

; (def readers (ImageIO/getImageReadersByFormatName "JPEG"))
; (.next readers)
; (def img (ImageIO/read (clojure.java.io/file "/tmp/img.jpg")))

(defn resize [image max-dim]
  (if (>= (.getHeight image)
          (.getWidth image))
    (collage/resize image :height max-dim)
    (collage/resize image :width max-dim)))

(def IMG-DATA-URL-PREFIX "data:image/jpeg;base64")

(defn buffered-image->data-url-img ^String [^BufferedImage img]
  (let [os (ByteArrayOutputStream.)
        _ (ImageIO/write img "jpg" os)
        ba (.toByteArray os)
        base64 (.encodeToString (Base64/getEncoder) ba)]
    (clojure.string/join "," [IMG-DATA-URL-PREFIX base64])))

(defn update-images [conf user]
  (if-not (:img_digest user)
    user
    (let [img (fetch-img conf user)]
      (assoc user
             :img256_url (-> img (resize 256) buffered-image->data-url-img)
             :img32_url (-> img (resize 32) buffered-image->data-url-img)))))

;#### debug ###################################################################
;(logging-config/set-logger! :level :debug)
;(logging-config/set-logger! :level :info)
;(debug/debug-ns 'cider-ci.utils.shutdown)
;(debug/debug-ns *ns*)
