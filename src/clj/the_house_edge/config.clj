(ns the-house-edge.config
  "Configuration management for The House Edge platform"
  (:require [environ.core :refer [env]]))

;; ============================================================================
;; Environment Configuration
;; ============================================================================

(def config
  "Application configuration loaded from environment variables"
  {:server
   {:port (Integer/parseInt (env :port "3000"))
    :host (env :host "0.0.0.0")}
   
   :database
   {:type :in-memory  ;; For MVP, use in-memory storage
    :persist-path (env :db-path "data/the-house-edge.edn")}
   
   :odds
   {:update-interval-ms 30000  ;; 30 seconds
    :bookmakers [:pinnacle :betfair :bet365 :draftkings]
    :mock-mode? true}  ;; Use mock data for MVP
   
   :sharp
   {:rlm-threshold 0.02        ;; 2% line movement against public
    :steam-threshold 0.015      ;; 1.5% synchronized movement
    :min-confidence 0.65        ;; Minimum confidence to signal
    :lookback-hours 48}         ;; Historical pattern lookback
   
   :bankroll
   {:default-strategy :kelly
    :flat-percentage 0.02       ;; 2% flat betting
    :kelly-fraction 0.25        ;; Quarter Kelly (conservative)
    :max-stake-percentage 0.05  ;; Never stake more than 5%
    :min-stake 10.0             ;; Minimum bet size
    :initial-bankroll 1000.0}   ;; Starting bankroll for demo
   
   :analysis
   {:xg-weight 0.4             ;; xG data weight in probability
    :form-weight 0.3            ;; Recent form weight
    :tactical-weight 0.2        ;; Tactical analysis weight
    :market-weight 0.1          ;; Market odds weight
    :form-decay 0.9             ;; Exponential decay for form
    :matches-lookback 10}       ;; Recent matches to analyze
   
   :slips
   {:min-ev 0.05               ;; 5% minimum edge to recommend
    :min-confidence 0.70        ;; 70% minimum confidence
    :max-daily-slips 10         ;; Limit recommendations
    :markets [:match-result     ;; Markets to cover
              :asian-handicap
              :totals
              :both-teams-score]}
   
   :performance
   {:report-frequency-hours 168  ;; Weekly reports
    :variance-tolerance 2.0       ;; 2 std deviations acceptable
    :min-sample-size 30}          ;; Minimum bets for statistics
   
   :security
   {:rate-limit-per-minute 60
    :max-request-size-kb 1024
    :allowed-origins ["http://localhost:3000"
                      "http://localhost:3449"]}  ;; Figwheel port
   
   :logging
   {:level (keyword (env :log-level "info"))
    :output (env :log-output "stdout")}})

;; ============================================================================
;; Configuration Accessors
;; ============================================================================

(defn get-config
  "Get configuration value by path (e.g., [:server :port])"
  [path]
  (get-in config path))

(defn server-port [] (get-config [:server :port]))
(defn server-host [] (get-config [:server :host]))

(defn mock-mode? [] (get-config [:odds :mock-mode?]))
(defn bookmakers [] (get-config [:odds :bookmakers]))

(defn default-strategy [] (get-config [:bankroll :default-strategy]))
(defn initial-bankroll [] (get-config [:bankroll :initial-bankroll]))
(defn kelly-fraction [] (get-config [:bankroll :kelly-fraction]))

(defn min-ev [] (get-config [:slips :min-ev]))
(defn min-confidence [] (get-config [:slips :min-confidence]))

;; ============================================================================
;; Mock Data Configuration
;; ============================================================================

(def mock-leagues
  "Leagues to generate mock data for"
  [{:id "EPL" :name "English Premier League"}
   {:id "LAL" :name "La Liga"}
   {:id "BUN" :name "Bundesliga"}
   {:id "SER" :name "Serie A"}
   {:id "LIG" :name "Ligue 1"}])

(def mock-teams
  "Teams for mock data generation"
  {:EPL ["Arsenal" "Chelsea" "Liverpool" "Man City" "Man United"
         "Tottenham" "Newcastle" "Brighton" "Aston Villa" "West Ham"]
   :LAL ["Real Madrid" "Barcelona" "Atletico Madrid" "Sevilla" "Real Sociedad"
         "Villarreal" "Athletic Bilbao" "Real Betis" "Valencia" "Girona"]
   :BUN ["Bayern Munich" "Borussia Dortmund" "RB Leipzig" "Bayer Leverkusen"
         "Union Berlin" "Freiburg" "Eintracht Frankfurt" "Wolfsburg" "Monchengladbach" "Stuttgart"]
   :SER ["Inter Milan" "AC Milan" "Juventus" "Napoli" "Roma"
         "Lazio" "Atalanta" "Fiorentina" "Bologna" "Torino"]
   :LIG ["PSG" "Monaco" "Marseille" "Lyon" "Lille"
         "Nice" "Lens" "Rennes" "Strasbourg" "Nantes"]})

(def mock-bookmakers
  "Bookmaker characteristics for mock data"
  {:pinnacle {:name "Pinnacle"
              :type :sharp
              :margin 0.02      ;; 2% margin (sharp line)
              :movement-speed :fast}
   :betfair {:name "Betfair Exchange"
             :type :exchange
             :margin 0.025     ;; 2.5% commission
             :movement-speed :instant}
   :bet365 {:name "Bet365"
            :type :soft
            :margin 0.06      ;; 6% margin
            :movement-speed :medium}
   :draftkings {:name "DraftKings"
                :type :soft
                :margin 0.055   ;; 5.5% margin
                :movement-speed :slow}})

;; ============================================================================
;; Validation
;; ============================================================================

(defn validate-config!
  "Validate configuration on startup"
  []
  (assert (pos? (server-port)) "Server port must be positive")
  (assert (>= (kelly-fraction) 0) "Kelly fraction must be non-negative")
  (assert (<= (kelly-fraction) 1) "Kelly fraction must be <= 1")
  (assert (pos? (initial-bankroll)) "Initial bankroll must be positive")
  (assert (>= (min-ev) 0) "Minimum EV must be non-negative")
  (assert (>= (min-confidence) 0) "Minimum confidence must be >= 0")
  (assert (<= (min-confidence) 1) "Minimum confidence must be <= 1")
  :valid)
