(ns the-house-edge.web.handler
  "Web server and API handlers"
  (:require [compojure.core :refer [defroutes GET POST]]
            [compojure.route :as route]
            [ring.middleware.json :refer [wrap-json-response wrap-json-body]]
            [ring.middleware.cors :refer [wrap-cors]]
            [ring.middleware.defaults :refer [wrap-defaults api-defaults]]
            [ring.adapter.jetty :refer [run-jetty]]
            [the-house-edge.config :as config]
            [the-house-edge.slips.core :as slips]
            [the-house-edge.performance.core :as performance]
            [the-house-edge.bankroll.core :as bankroll]
            [the-house-edge.odds.core :as odds]
            [taoensso.timbre :as log]))

;; ============================================================================
;; Server State
;; ============================================================================

(defonce server (atom nil))

;; ============================================================================
;; API Handlers
;; ============================================================================

(defn health-check
  "Health check endpoint"
  [_]
  {:status 200
   :body {:status "ok"
          :service "The House Edge"
          :version "0.1.0-SNAPSHOT"}})

(defn get-recommendations
  "Get current betting recommendations"
  [_]
  (try
    (let [recs (slips/get-recommendations)
          formatted-slips (mapv slips/format-slip-for-display (:slips recs))]
      {:status 200
       :body {:success true
              :data {:total-matches (:total-matches recs)
                     :total-slips (:total-slips recs)
                     :recommended-count (:recommended-slips recs)
                     :recommendations formatted-slips}}})
    (catch Exception e
      (log/error e "Error getting recommendations")
      {:status 500
       :body {:success false
              :error "Failed to get recommendations"}})))

(defn get-performance
  "Get performance metrics"
  [_]
  (try
    (let [metrics (performance/get-performance-metrics)]
      {:status 200
       :body {:success true
              :data metrics}})
    (catch Exception e
      (log/error e "Error getting performance metrics")
      {:status 500
       :body {:success false
              :error "Failed to get performance metrics"}})))

(defn get-bankroll-status
  "Get current bankroll status"
  [_]
  (try
    (let [current (bankroll/current-bankroll)
          snapshot (bankroll/create-bankroll-snapshot)
          drawdown (bankroll/current-drawdown)
          risk-check (bankroll/check-loss-limits)]
      {:status 200
       :body {:success true
              :data {:current-bankroll current
                     :snapshot snapshot
                     :drawdown drawdown
                     :risk-alert risk-check}}})
    (catch Exception e
      (log/error e "Error getting bankroll status")
      {:status 500
       :body {:success false
              :error "Failed to get bankroll status"}})))

(defn get-variance-report
  "Get variance analysis"
  [_]
  (try
    (let [report (performance/get-variance-report)]
      {:status 200
       :body {:success true
              :data report}})
    (catch Exception e
      (log/error e "Error getting variance report")
      {:status 500
       :body {:success false
              :error "Failed to get variance report"}})))

(defn get-bet-history
  "Get betting history"
  [request]
  (try
    (let [params (get-in request [:params])
          ledger (bankroll/get-ledger)
          filtered (cond-> ledger
                     (:market params) (performance/query-by-market (keyword (:market params)))
                     (:strategy params) (performance/query-by-strategy (keyword (:strategy params))))]
      {:status 200
       :body {:success true
              :data {:total-bets (count filtered)
                     :bets filtered}}})
    (catch Exception e
      (log/error e "Error getting bet history")
      {:status 500
       :body {:success false
              :error "Failed to get bet history"}})))

;; ============================================================================
;; Routes
;; ============================================================================

(defroutes app-routes
  ;; Health check
  (GET "/health" [] health-check)
  
  ;; API endpoints
  (GET "/api/recommendations" [] get-recommendations)
  (GET "/api/performance" [] get-performance)
  (GET "/api/bankroll" [] get-bankroll-status)
  (GET "/api/variance" [] get-variance-report)
  (GET "/api/history" [] get-bet-history)
  
  ;; Static files
  (route/resources "/")
  
  ;; 404
  (route/not-found {:status 404
                    :body {:success false
                           :error "Not found"}}))

;; ============================================================================
;; Middleware
;; ============================================================================

(def app
  (-> app-routes
      (wrap-defaults api-defaults)
      wrap-json-response
      (wrap-json-body {:keywords? true})
      (wrap-cors :access-control-allow-origin (config/get-config [:security :allowed-origins])
                 :access-control-allow-methods [:get :post]
                 :access-control-allow-headers ["Content-Type"])))

;; ============================================================================
;; Server Control
;; ============================================================================

(defn start-server!
  "Start the web server"
  []
  (when-not @server
    (let [port (config/server-port)
          jetty-server (run-jetty app {:port port
                                       :join? false})]
      (reset! server jetty-server)
      (log/info (str "Web server started on port " port)))))

(defn stop-server!
  "Stop the web server"
  []
  (when @server
    (.stop @server)
    (reset! server nil)
    (log/info "Web server stopped")))
