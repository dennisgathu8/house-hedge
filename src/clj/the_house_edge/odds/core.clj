(ns the-house-edge.odds.core
  "Agent Alpha: The Odds Engine
   Real-time odds ingestion and expected value calculation"
  (:require [the-house-edge.protocol :as p]
            [the-house-edge.config :as config]
            [the-house-edge.util :as util]
            [the-house-edge.mock :as mock]
            [clojure.core.async :as async :refer [go go-loop chan <! >! >!! timeout]]))

;; ============================================================================
;; State Management
;; ============================================================================

(defonce odds-store (atom []))

(defonce ev-store (atom []))

(defonce odds-channels
  {:updates (chan 100)
   :ev-results (chan 100)})

;; ============================================================================
;; Odds Ingestion
;; ============================================================================

(defn fetch-odds
  "Fetch odds for a match from a bookmaker (mock implementation)"
  [match-id bookmaker market]
  (when (config/mock-mode?)
    ;; In mock mode, generate realistic odds
    (let [match {:id match-id}
          true-probs (mock/generate-true-probabilities "Home" "Away")
          odds (mock/generate-odds match-id bookmaker true-probs)]
      odds)))

(defn ingest-odds
  "Ingest odds update and store in immutable history"
  [odds]
  (swap! odds-store conj odds)
  (>!! (:updates odds-channels) odds)
  odds)

(defn get-latest-odds
  "Get latest odds for a match/market/bookmaker"
  [match-id market bookmaker]
  (->> @odds-store
       (filter #(and (= (:match-id %) match-id)
                    (= (:market %) market)
                    (= (:bookmaker %) bookmaker)))
       (sort-by :timestamp)
       last))

(defn get-all-odds-for-match
  "Get all current odds for a match across bookmakers"
  [match-id market]
  (let [bookmakers (config/bookmakers)]
    (mapv #(get-latest-odds match-id market (name %)) bookmakers)))

;; ============================================================================
;; Expected Value Calculation
;; ============================================================================

(defn calculate-true-probability
  "Calculate true probability from market odds (remove margin)"
  [odds-vector]
  (util/remove-margin odds-vector))

(defn calculate-ev
  "Calculate expected value for a bet
   EV = (true-probability × odds) - 1"
  [true-prob odds]
  (util/calculate-ev true-prob odds))

(defn find-best-odds
  "Find best odds across all bookmakers for a selection"
  [match-id market selection-index]
  (let [all-odds (->> (get-all-odds-for-match match-id market)
                      (remove nil?))
        odds-with-bookmaker (map (fn [odds]
                                   {:bookmaker (:bookmaker odds)
                                    :odds (nth (:prices odds) selection-index)
                                    :timestamp (:timestamp odds)})
                                 all-odds)]
    (if (seq odds-with-bookmaker)
      (apply max-key :odds odds-with-bookmaker)
      ;; Return a dummy object with 0 odds if no data available
      {:bookmaker "None" :odds 0.0 :timestamp (util/now)})))

(defn calculate-ev-for-selection
  "Calculate EV for a specific selection using best available odds"
  [match-id market selection-index true-prob]
  (let [best-odds (find-best-odds match-id market selection-index)
        ev (calculate-ev true-prob (:odds best-odds))
        kelly-frac (config/kelly-fraction)
        bankroll (config/initial-bankroll)
        kelly-stake (when (pos? ev)
                     (util/kelly-criterion bankroll ev (:odds best-odds) kelly-frac))]
    {:match-id match-id
     :market market
     :selection (case selection-index
                  0 "Home"
                  1 "Draw"
                  2 "Away")
     :bookmaker (:bookmaker best-odds)
     :odds (:odds best-odds)
     :true-probability true-prob
     :ev ev
     :kelly-fraction kelly-stake
     :timestamp (util/now)}))

(defn analyze-match-ev
  "Analyze all selections for a match and find value bets"
  [match-data]
  (let [match-id (get-in match-data [:match :id])
        market :match-result
        true-probs (:true-probs match-data)
        selections [{:index 0 :prob (:home true-probs)}
                   {:index 1 :prob (:draw true-probs)}
                   {:index 2 :prob (:away true-probs)}]
        ev-results (mapv #(calculate-ev-for-selection 
                           match-id 
                           market 
                           (:index %) 
                           (:prob %))
                        selections)
        value-bets (filter #(> (:ev %) (config/min-ev)) ev-results)]
    {:match match-data
     :ev-results ev-results
     :value-bets value-bets}))

;; ============================================================================
;; Kelly Criterion
;; ============================================================================

(defn kelly-stake
  "Calculate Kelly Criterion stake size
   Kelly% = (edge / (odds - 1))
   Fractional Kelly for risk management"
  [bankroll edge odds]
  (let [kelly-frac (config/kelly-fraction)
        max-stake-pct (get-in config/config [:bankroll :max-stake-percentage])
        min-stake (get-in config/config [:bankroll :min-stake])
        stake (util/kelly-criterion bankroll edge odds kelly-frac)
        clamped-stake (util/clamp stake min-stake (* bankroll max-stake-pct))]
    {:recommended-stake clamped-stake
     :stake-percentage (/ clamped-stake bankroll)
     :kelly-fraction kelly-frac
     :full-kelly (* bankroll (/ edge (dec odds)))}))

;; ============================================================================
;; Arbitrage Detection
;; ============================================================================

(defn detect-arbitrage
  "Detect arbitrage opportunities across bookmakers
   Arbitrage exists when: 1/odds1 + 1/odds2 + 1/odds3 < 1"
  [match-id market]
  (let [all-odds (get-all-odds-for-match match-id market)
        num-selections (count (:prices (first all-odds)))]
    (when (seq all-odds)
      ;; For each selection, find best odds
      (let [best-odds-per-selection
            (for [i (range num-selections)]
              (let [odds-for-selection (map #(nth (:prices %) i) all-odds)
                    bookmakers (map :bookmaker all-odds)
                    best-idx (apply max-key second (map-indexed vector odds-for-selection))
                    best-odds (second best-idx)
                    best-bookmaker (nth bookmakers (first best-idx))]
                {:selection-index i
                 :odds best-odds
                 :bookmaker best-bookmaker}))
            
            ;; Calculate total implied probability
            total-prob (reduce + (map #(/ 1.0 (:odds %)) best-odds-per-selection))
            
            ;; Arbitrage exists if total < 1
            is-arb? (< total-prob 1.0)
            profit-margin (when is-arb? (- 1.0 total-prob))]
        
        (when is-arb?
          {:match-id match-id
           :market market
           :bookmakers (mapv :bookmaker best-odds-per-selection)
           :selections (mapv :selection-index best-odds-per-selection)
           :odds (mapv :odds best-odds-per-selection)
           :profit-margin profit-margin
           :timestamp (util/now)})))))

;; ============================================================================
;; Historical Line Movement
;; ============================================================================

(defn get-line-history
  "Get historical line movement for a match"
  [match-id market bookmaker]
  (->> @odds-store
       (filter #(and (= (:match-id %) match-id)
                    (= (:market %) market)
                    (= (:bookmaker %) bookmaker)))
       (sort-by :timestamp)
       vec))

(defn calculate-line-movement
  "Calculate line movement magnitude"
  [opening-odds current-odds]
  (let [movements (map (fn [open curr]
                        (/ (- curr open) open))
                      opening-odds
                      current-odds)]
    (if (seq movements)
      {:movements movements
       :max-movement (apply max (map #(Math/abs %) movements))
       :direction (if (pos? (or (first movements) 0)) :lengthening :shortening)}
      {:movements [] :max-movement 0.0 :direction :none})))

;; ============================================================================
;; Odds Stream Processing
;; ============================================================================

(defn start-odds-stream
  "Start processing odds updates from channel"
  []
  (go-loop []
    (when-let [odds (<! (:updates odds-channels))]
      ;; Process odds update
      (let [ev-calc (when-let [true-prob 0.5]  ;; Simplified for now
                     (calculate-ev true-prob (first (:prices odds))))]
        (when (and ev-calc (pos? ev-calc))
          (>! (:ev-results odds-channels) 
              {:odds odds :ev ev-calc})))
      (recur))))

(defn stop-odds-stream
  "Stop odds stream processing"
  []
  (async/close! (:updates odds-channels))
  (async/close! (:ev-results odds-channels)))

;; ============================================================================
;; Public API
;; ============================================================================

(defn initialize!
  "Initialize odds engine"
  []
  (reset! odds-store [])
  (reset! ev-store [])
  (start-odds-stream)
  :initialized)

(defn shutdown!
  "Shutdown odds engine"
  []
  (stop-odds-stream)
  :shutdown)

(defn scan-weekend-fixtures
  "Scan weekend fixtures for value bets"
  []
  (let [fixtures (mock/generate-weekend-slate)
        ;; Ingest all odds
        _ (doseq [fixture fixtures
                  odds (:odds fixture)]
            (ingest-odds odds))
        ;; Analyze for EV
        analyses (mapv analyze-match-ev fixtures)
        ;; Filter for value
        value-matches (filter #(seq (:value-bets %)) analyses)]
    {:total-matches (count fixtures)
     :value-matches (count value-matches)
     :analyses value-matches}))

(defn get-value-bets
  "Get all current value bets above threshold"
  [min-ev]
  (let [analyses (scan-weekend-fixtures)]
    (->> (:analyses analyses)
         (mapcat :value-bets)
         (filter #(> (:ev %) min-ev))
         (sort-by :ev >))))
