(defproject the-house-edge "0.1.0-SNAPSHOT"
  :description "Professional Betting Intelligence Platform - Investment-grade sports betting analysis"
  :url "https://github.com/dennisgathu8/the-house-edge"
  :license {:name "MIT"
            :url "https://opensource.org/licenses/MIT"}
  
  :dependencies [[org.clojure/clojure "1.11.1"]
                 [org.clojure/clojurescript "1.11.60"]
                 [org.clojure/core.async "1.6.673"]
                 [org.clojure/data.json "2.4.0"]
                 
                 ;; Web server
                 [ring/ring-core "1.10.0"]
                 [ring/ring-jetty-adapter "1.10.0"]
                 [ring/ring-json "0.5.1"]
                 [ring/ring-defaults "0.3.4"]
                 [compojure "1.7.0"]
                 [ring-cors "0.1.13"]
                 
                 ;; Frontend
                 [reagent "1.2.0"]
                 [re-frame "1.3.0"]
                 [day8.re-frame/http-fx "0.2.4"]
                 
                 ;; HTTP client
                 [clj-http "3.12.3"]
                 
                 ;; Time manipulation
                 [clj-time "0.15.2"]
                 
                 ;; Data validation
                 [prismatic/schema "1.4.1"]
                 
                 ;; Logging
                 [com.taoensso/timbre "6.1.0"]
                 
                 ;; Environment variables
                 [environ "1.2.0"]
                 
                 ;; Jackson dependencies (explicit versions to resolve conflicts)
                 [com.fasterxml.jackson.core/jackson-core "2.15.2"]
                 [com.fasterxml.jackson.core/jackson-databind "2.15.2"]
                 [com.fasterxml.jackson.dataformat/jackson-dataformat-cbor "2.15.2"]
                 [com.fasterxml.jackson.dataformat/jackson-dataformat-smile "2.15.2"]]
  
  :plugins [[lein-cljsbuild "1.1.8"]
            [lein-figwheel "0.5.20"]
            [lein-environ "1.2.0"]]
  
  :source-paths ["src/clj" "src/cljc"]
  
  :cljsbuild {:builds
              [{:id "dev"
                :source-paths ["src/cljs" "src/cljc"]
                :figwheel {:on-jsload "the-house-edge.web.core/mount-root"}
                :compiler {:main the-house-edge.web.core
                           :asset-path "js/compiled/out"
                           :output-to "resources/public/js/compiled/app.js"
                           :output-dir "resources/public/js/compiled/out"
                           :source-map-timestamp true
                           :preloads [devtools.preload]}}
               
               {:id "min"
                :source-paths ["src/cljs" "src/cljc"]
                :compiler {:main the-house-edge.web.core
                           :output-to "resources/public/js/compiled/app.js"
                           :optimizations :advanced
                           :pretty-print false}}]}
  
  :figwheel {:css-dirs ["resources/public/css"]
             :ring-handler the-house-edge.web.handler/app}
  
  :profiles {:dev {:dependencies [[binaryage/devtools "1.0.7"]
                                  [figwheel-sidecar "0.5.20"]]
                   :source-paths ["src/clj" "src/cljc" "dev"]}
             
             :uberjar {:aot :all
                       :prep-tasks ["compile" ["cljsbuild" "once" "min"]]}}
  
  :main ^:skip-aot the-house-edge.core
  :target-path "target/%s"
  :uberjar-name "the-house-edge-standalone.jar"
  
  :aliases {"dev" ["do" ["clean"] ["figwheel"]]
            "build" ["do" ["clean"] ["cljsbuild" "once" "min"]]})
