(defproject leihs_zhdk-sync "0.1.0-SNAPSHOT"
  :description "Sync groups from ZAPI into leihs.zhdk"
  :url "https://github.com/Madek/leihs_zhdk-sync"
  :license {:name "WTFPL"
            :url "http://www.wtfpl.net/txt/copying/"}
  :dependencies [
                 [cheshire "5.8.0"]
                 [clj-http "3.9.0"]
                 [environ "1.1.0"]
                 [log4j/log4j "1.2.17" :exclusions [javax.mail/mail javax.jms/jms com.sun.jdmk/jmxtools com.sun.jmx/jmxri]]
                 [logbug "4.2.2"]
                 [org.clojure/clojure "1.9.0"]
                 [org.clojure/tools.cli "0.3.7"]
                 [org.slf4j/slf4j-log4j12 "1.7.25"]
                 [progrock "0.1.2"]
                 [timothypratley/patchin "0.3.5"]
                 ]


  ; jdk 9 needs ["--add-modules" "java.xml.bind"]
  :jvm-opts #=(eval (if (re-matches #"^9\..*" (System/getProperty "java.version"))
                      ["--add-modules" "java.xml.bind"]
                      []))

  :javac-options ["-target" "1.8" "-source" "1.8" "-Xlint:-options"]

  :main leihs.zhdk-sync.main

  :resource-paths ["resources/all"]

  :source-paths ["src/all"] 

  :test-paths ["src/test"]

  :plugins [[lein-environ "1.1.0"]]

  :profiles {:dev {:env {:dev true}
                   :resource-paths ["resources/dev"]}
             :test {:env {:test true}
                    :resource-paths ["resources/test"]
                    :source-paths ["src/test"]}
             :uberjar {:aot :all
                       :resource-paths ["resources/prod"]
                       :uberjar-name "../leihs_zhdk-sync.jar"}})
