(ns the-house-edge.web.middleware
  "Security middleware for API endpoints"
  (:require [the-house-edge.config :as config]
            [taoensso.timbre :as log]
            [clojure.string :as str]
            [clojure.data.json :as json])
  (:import [java.time Instant Duration]))

;; ============================================================================
;; Rate Limiting
;; ============================================================================

(defonce rate-limit-store (atom {}))

(defn- clean-rate-limits!
  "Clean up old rate limit entries (simple garbage collection)"
  []
  (let [now (Instant/now)
        ttl (Duration/ofMinutes 1)]
    (swap! rate-limit-store
           (fn [store]
             (into {} (remove (fn [[_ requests]]
                                (every? #(> (.toMillis (Duration/between % now)) (.toMillis ttl)) requests))
                              store))))))

(defn wrap-rate-limit
  "Simple sliding window rate limiter scoped to 1 minute"
  [handler]
  (fn [request]
    (let [ip (:remote-addr request)
          ;; Occasional cleanup to prevent memory leak
          _ (when (= 0 (rand-int 100)) (clean-rate-limits!))
          now (Instant/now)
          window (Duration/ofMinutes 1)
          limit (config/get-config [:security :rate-limit-per-minute])]
      
      (let [new-store (swap! rate-limit-store
                             (fn [store]
                               (let [requests (get store ip [])
                                     recent-requests (filterv #(< (.toMillis (Duration/between % now)) (.toMillis window)) requests)]
                                 (assoc store ip (conj recent-requests now)))))
            recent-count (count (get new-store ip))]
        
        (log/debug "Rate limit state for IP:" ip " Count:" recent-count)
        (if (> recent-count limit)
          (do
            (log/warn (str "Rate limit exceeded (" limit "/min) for IP: " ip))
            {:status 429
             :headers {"Content-Type" "application/json"}
             :body (json/write-str {:success false :error "Too Many Requests. Rate limit exceeded."})})
          (handler request))))))

;; ============================================================================
;; API Key Authentication
;; ============================================================================

(defn wrap-api-key-auth
  "Require X-API-Key header for all /api/ routes"
  [handler]
  (fn [request]
    (let [uri (:uri request)
          api-keys (config/get-config [:security :api-keys])
          provided-key (get-in request [:headers "x-api-key"])]
      (if (str/starts-with? uri "/api/")
        (if (some #{provided-key} api-keys)
          ;; Valid key -> pass to next handler
          (handler request)
          ;; Invalid or missing key -> reject
          (do
            (log/warn (str "Unauthorized API access attempt to " uri " from " (:remote-addr request)))
            {:status 401
             :headers {"Content-Type" "application/json"}
             :body (json/write-str {:success false :error "Unauthorized: Invalid or missing X-API-Key"})}))
        ;; Not an /api/ route -> bypass auth
        (handler request)))))
