(ns the-house-edge.core
  "Main system entry point - coordinates all agents"
  (:require [the-house-edge.config :as config]
            [the-house-edge.odds.core :as odds]
            [the-house-edge.sharp.core :as sharp]
            [the-house-edge.bankroll.core :as bankroll]
            [the-house-edge.analysis.core :as analysis]
            [the-house-edge.slips.core :as slips]
            [the-house-edge.performance.core :as performance]
            [the-house-edge.web.handler :as web]
            [taoensso.timbre :as log])
  (:gen-class))

;; ============================================================================
;; System State
;; ============================================================================

(defonce system-state
  (atom {:initialized? false
         :agents {}
         :start-time nil}))

;; ============================================================================
;; System Lifecycle
;; ============================================================================

(defn initialize-agents!
  "Initialize all agents"
  []
  (log/info "Initializing The House Edge agents...")
  
  (config/validate-config!)
  (log/info "✓ Configuration validated")
  
  (odds/initialize!)
  (log/info "✓ Agent Alpha (Odds Engine) initialized")
  
  (sharp/initialize!)
  (log/info "✓ Agent Beta (Sharp Detector) initialized")
  
  (bankroll/initialize!)
  (log/info "✓ Agent Gamma (Bankroll Guardian) initialized")
  
  (analysis/initialize!)
  (log/info "✓ Agent Delta (Match Analyst) initialized")
  
  (slips/initialize!)
  (log/info "✓ Agent Epsilon (Slip Generator) initialized")
  
  (performance/initialize!)
  (log/info "✓ Agent Zeta (Performance Oracle) initialized")
  
  (swap! system-state assoc
         :initialized? true
         :agents {:odds true
                  :sharp true
                  :bankroll true
                  :analysis true
                  :slips true
                  :performance true}
         :start-time (java.util.Date.))
  
  (log/info "All agents initialized successfully"))

(defn shutdown-agents!
  "Shutdown all agents"
  []
  (log/info "Shutting down The House Edge...")
  
  (odds/shutdown!)
  (log/info "✓ Agent Alpha shutdown")
  
  (swap! system-state assoc :initialized? false)
  (log/info "System shutdown complete"))

;; ============================================================================
;; Professional Gambler Demo
;; ============================================================================

(defn run-professional-gambler-demo
  "Execute the 'Professional Gambler' demonstration"
  []
  (log/info "\n=== THE HOUSE EDGE: Professional Gambler Demo ===\n")
  
  ;; Step 1: Pre-match Analysis
  (log/info "Step 1: Scanning weekend fixtures...")
  (let [recommendations (slips/get-recommendations)
        value-count (:recommended-slips recommendations)]
    (log/info (str "✓ Scanned " (:total-matches recommendations) " matches"))
    (log/info (str "✓ Found " value-count " value bets with +EV > 5%"))
    
    ;; Step 2: Display top recommendations
    (log/info "\nStep 2: Top Investment-Grade Recommendations:")
    (doseq [[idx slip] (map-indexed vector (take 5 (:slips recommendations)))]
      (let [formatted (slips/format-slip-for-display slip)]
        (log/info (str "\n" (inc idx) ". " (:match formatted)))
        (log/info (str "   League: " (:league formatted)))
        (log/info (str "   Selection: " (:selection formatted) " @ " (:odds formatted)))
        (log/info (str "   Edge: " (:ev formatted) " | Confidence: " (:confidence formatted)))
        (log/info (str "   Recommended Stake: " (:stake formatted)))
        (log/info (str "   Rationale: " (:rationale formatted)))
        (when (pos? (:sharp-signals formatted))
          (log/info (str "   ⚡ " (:sharp-signals formatted) " sharp money signal(s) detected")))))
    
    ;; Step 3: Bankroll Status
    (log/info "\nStep 3: Bankroll Status:")
    (let [current (bankroll/current-bankroll)
          snapshot (bankroll/create-bankroll-snapshot)]
      (log/info (str "   Current Bankroll: $" current))
      (log/info (str "   Peak Bankroll: $" (:peak-balance snapshot)))
      (log/info (str "   ROI: " (the-house-edge.util/format-roi (:roi snapshot)))))
    
    ;; Step 4: Performance Metrics
    (log/info "\nStep 4: Performance Analytics:")
    (let [metrics (performance/get-performance-metrics)]
      (log/info (str "   Total Bets: " (:total-bets metrics)))
      (log/info (str "   Win Rate: " (the-house-edge.util/percentage 
                                      (if (pos? (:total-bets metrics))
                                        (/ (:won metrics) (:total-bets metrics))
                                        0))))
      (log/info (str "   ROI: " (the-house-edge.util/format-roi (:roi metrics))))
      (log/info (str "   Sharpe Ratio: " (:sharpe-ratio metrics))))
    
    ;; Step 5: Variance Report
    (log/info "\nStep 5: Variance Analysis:")
    (let [variance (performance/get-variance-report)]
      (log/info (str "   " (:analysis variance))))
    
    (log/info "\n=== Demo Complete ===\n")
    recommendations))

;; ============================================================================
;; Main Entry Point
;; ============================================================================

(defn -main
  "Main entry point"
  [& args]
  (try
    (log/info "Starting The House Edge Platform...")
    (log/info (str "Version: 0.1.0-SNAPSHOT"))
    (log/info (str "Mode: " (if (config/mock-mode?) "MOCK DATA (MVP)" "LIVE DATA")))
    
    ;; Initialize system
    (initialize-agents!)
    
    ;; Run demo if requested
    (when (some #{"--demo"} args)
      (run-professional-gambler-demo))
    
    ;; Start web server
    (when-not (some #{"--no-server"} args)
      (log/info (str "\nStarting web server on port " (config/server-port) "..."))
      (web/start-server!)
      (log/info (str "✓ Server running at http://" (config/server-host) ":" (config/server-port)))
      (log/info "\nThe House Edge is ready. Press Ctrl+C to stop."))
    
    ;; Keep alive
    (when-not (some #{"--demo"} args)
      @(promise))
    
    (catch Exception e
      (log/error e "Fatal error during startup")
      (System/exit 1))))
