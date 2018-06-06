(defproject xmaslist "0.1.0-SNAPSHOT"
  :description "christmas list manager"
  :dependencies [[org.clojure/clojure "1.8.0"]
                 [ring "1.4.0"]
                 [compojure "1.3.4"]
                 [hiccup "1.0.5"]
                 [org.clojure/java.jdbc "0.7.6"]
                 [org.xerial/sqlite-jdbc "3.23.1"]]
  :main ^:skip-aot xmaslist.core
  :target-path "target/%s"
  :profiles {:uberjar {:aot :all}
             :dev {:main xmaslist.core/-dev-main}})
