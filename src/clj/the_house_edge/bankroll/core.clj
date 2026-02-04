(ns the-house-edge.bankroll.core
  "Agent Gamma: The Bankroll Guardian
   Professional staking strategies with immutable audit trail"
  (:require [the-house-edge.protocol :as p]
            [the-house-edge.config :as config]
            [the-house-edge.util :as util]
            [taoensso.timbre :as log]
            [clojure.edn :as edn]
            [clojure.java.io :as io]))

;; ============================================================================
;; State Management - Immutable Ledger
;; ============================================================================

(defonce bet-ledger (atom []))

(defonce bankroll-snapshots (atom []))

;; ============================================================================
;; Persistence
;; ============================================================================

(defn save-ledger!
  "Save ledger to persistent storage"
  []
  (let [path (config/get-config [:database :persist-path])]
    (io/make-parents path)
    (spit path (pr-str @bet-ledger))))

(defn load-ledger!
  "Load ledger from persistent storage"
  []
  (let [path (config/get-config [:database :persist-path])]
    (when (.exists (io/as-file path))
      (reset! bet-ledger (edn/read-string (slurp path))))))

;; ============================================================================
;; Staking Strategies
;; ============================================================================

(defn flat-betting-stake
  "Flat betting: Fixed percentage of bankroll"
  [bankroll]
  (let [flat-pct (config/get-config [:bankroll :flat-percentage])]
    (* bankroll flat-pct)))

(defn kelly-criterion-stake
  "Kelly Criterion: Optimal growth stake sizing"
  [bankroll edge odds]
  (let [kelly-frac (config/kelly-fraction)
        max-stake-pct (config/get-config [:bankroll :max-stake-percentage])
        min-stake (config/get-config [:bankroll :min-stake])
        stake (util/kelly-criterion bankroll edge odds kelly-frac)
        clamped (util/clamp stake min-stake (* bankroll max-stake-pct))]
    clamped))

(defn confidence-weighted-stake
  "Confidence-weighted: 1-5 unit scale based on edge quality"
  [bankroll confidence ev]
  (let [base-unit (* bankroll 0.01)  ;; 1% = 1 unit
        ;; Scale units based on confidence and EV
        units (cond
                (and (> confidence 0.85) (> ev 0.10)) 5.0
                (and (> confidence 0.80) (> ev 0.08)) 4.0
                (and (> confidence 0.75) (> ev 0.06)) 3.0
                (and (> confidence 0.70) (> ev 0.05)) 2.0
                :else 1.0)
        stake (* base-unit units)
        max-stake (* bankroll (config/get-config [:bankroll :max-stake-percentage]))]
    (min stake max-stake)))

(defn calculate-stake
  "Calculate stake based on strategy"
  [strategy bankroll & {:keys [edge odds confidence ev]}]
  (case strategy
    :flat (flat-betting-stake bankroll)
    :kelly (kelly-criterion-stake bankroll edge odds)
    :confidence (confidence-weighted-stake bankroll confidence ev)
    (flat-betting-stake bankroll)))

;; ============================================================================
;; Bet Recording (Immutable)
;; ============================================================================

(defn record-bet
  "Record a bet in the immutable ledger"
  [bet]
  (swap! bet-ledger conj bet)
  (save-ledger!)
  bet)

(defn create-bet
  "Create a new bet record"
  [match-id market selection odds stake strategy ev]
  {:id (util/uuid)
   :match-id match-id
   :market market
   :selection selection
   :odds odds
   :stake stake
   :strategy strategy
   :ev ev
   :timestamp (util/now)
   :result :pending})

(defn settle-bet
  "Settle a bet (creates new record, doesn't mutate)"
  [bet-id result profit]
  (if-let [bet (util/find-by #(= (:id %) bet-id) @bet-ledger)]
    (let [settled-bet (assoc bet
                            :result result
                            :settled-at (util/now)
                            :profit profit)]
      (swap! bet-ledger
             (fn [ledger]
               (mapv #(if (= (:id %) bet-id) settled-bet %) ledger)))
      (save-ledger!)
      settled-bet)
    (log/error "Bet not found for settlement:" bet-id)))

;; ============================================================================
;; Bankroll Calculation
;; ============================================================================

(defn current-bankroll
  "Calculate current bankroll from ledger"
  []
  (let [initial (config/initial-bankroll)
        settled-bets (filter #(not= (:result %) :pending) @bet-ledger)
        total-profit (reduce + 0 (map #(or (:profit %) 0) settled-bets))]
    (+ initial total-profit)))

(defn peak-bankroll
  "Calculate peak bankroll achieved"
  []
  (let [initial (config/initial-bankroll)
        settled-bets (filter #(not= (:result %) :pending) @bet-ledger)
        running-balances (reductions
                          (fn [balance bet]
                            (+ balance (or (:profit bet) 0)))
                          initial
                          settled-bets)]
    (apply max initial running-balances)))

(defn create-bankroll-snapshot
  "Create point-in-time bankroll snapshot"
  []
  (let [balance (current-bankroll)
        peak (peak-bankroll)
        settled (filter #(not= (:result %) :pending) @bet-ledger)
        total-staked (reduce + 0 (map :stake settled))
        total-profit (reduce + 0 (map #(or (:profit %) 0) settled))
        roi (if (pos? total-staked) (/ total-profit total-staked) 0)]
    {:timestamp (util/now)
     :balance balance
     :peak-balance peak
     :total-staked total-staked
     :total-profit total-profit
     :bet-count (count settled)
     :roi roi}))

;; ============================================================================
;; Fork Simulation
;; ============================================================================

(defn simulate-alternative-strategy
  "Fork analysis: What if we used a different strategy?"
  [alternative-strategy]
  (let [initial (config/initial-bankroll)]
    (reduce
     (fn [state bet]
       (let [bankroll (:bankroll state)
             ;; Recalculate stake with alternative strategy
             new-stake (calculate-stake alternative-strategy bankroll
                                       :edge (:ev bet)
                                       :odds (:odds bet)
                                       :confidence 0.75
                                       :ev (:ev bet))
             ;; Apply same result
             profit (case (:result bet)
                     :won (* new-stake (dec (:odds bet)))
                     :lost (- new-stake)
                     :void 0
                     0)
             new-bankroll (+ bankroll profit)]
         {:bankroll new-bankroll
          :bets (conj (:bets state) (assoc bet :stake new-stake :profit profit))}))
     {:bankroll initial :bets []}
     @bet-ledger)))

(defn compare-strategies
  "Compare performance of different strategies on same bet history"
  []
  (let [current-strategy (config/default-strategy)
        strategies [:flat :kelly :confidence]
        results (map (fn [strategy]
                      (let [sim (simulate-alternative-strategy strategy)
                            final-bankroll (:bankroll sim)
                            roi (/ (- final-bankroll (config/initial-bankroll))
                                  (config/initial-bankroll))]
                        {:strategy strategy
                         :final-bankroll final-bankroll
                         :roi roi}))
                    strategies)]
    (sort-by :final-bankroll > results)))

;; ============================================================================
;; Risk Management
;; ============================================================================

(defn current-drawdown
  "Calculate current drawdown from peak"
  []
  (let [current (current-bankroll)
        peak (peak-bankroll)
        drawdown (- peak current)
        drawdown-pct (if (pos? peak) (/ drawdown peak) 0)]
    {:drawdown drawdown
     :drawdown-percentage drawdown-pct
     :from-peak peak
     :current current}))

(defn check-loss-limits
  "Check if loss limits should trigger warnings"
  []
  (let [dd (current-drawdown)
        dd-pct (:drawdown-percentage dd)]
    (cond
      (> dd-pct 0.30) {:alert :critical
                       :message "30% drawdown - Consider stopping"}
      (> dd-pct 0.20) {:alert :warning
                       :message "20% drawdown - Review strategy"}
      (> dd-pct 0.10) {:alert :caution
                       :message "10% drawdown - Monitor closely"}
      :else {:alert :ok
             :message "Within acceptable variance"})))

;; ============================================================================
;; Staking Decision
;; ============================================================================

(defn make-staking-decision
  "Make a staking decision for a potential bet"
  [match-id market selection odds ev confidence]
  (let [bankroll (current-bankroll)
        strategy (config/default-strategy)
        stake (calculate-stake strategy bankroll
                              :edge ev
                              :odds odds
                              :confidence confidence
                              :ev ev)
        stake-pct (/ stake bankroll)
        
        rationale (case strategy
                   :flat (str "Flat betting: " (util/percentage (config/get-config [:bankroll :flat-percentage])) " of bankroll")
                   :kelly (str "Kelly Criterion (" (util/percentage (config/kelly-fraction)) " Kelly) based on " (util/format-ev ev) " edge")
                   :confidence (str "Confidence-weighted: " confidence " confidence, " (util/format-ev ev) " EV")
                   "Default flat betting")]
    
    {:bet-id (util/uuid)
     :strategy strategy
     :bankroll bankroll
     :recommended-stake (util/round stake 2)
     :stake-percentage (util/round stake-pct 4)
     :rationale rationale
     :risk-check (check-loss-limits)}))

;; ============================================================================
;; Public API
;; ============================================================================

(defn initialize!
  "Initialize bankroll guardian"
  []
  (reset! bet-ledger [])
  (reset! bankroll-snapshots [])
  (load-ledger!)
  :initialized)

(defn get-ledger
  "Get complete bet ledger (immutable history)"
  []
  @bet-ledger)

(defn get-pending-bets
  "Get all pending bets"
  []
  (filter #(= (:result %) :pending) @bet-ledger))

(defn get-settled-bets
  "Get all settled bets"
  []
  (filter #(not= (:result %) :pending) @bet-ledger))
