(ns the-house-edge.performance.core
  "Agent Zeta: The Performance Oracle
   ROI tracking and variance analysis"
  (:require [the-house-edge.protocol :as p]
            [the-house-edge.config :as config]
            [the-house-edge.util :as util]
            [the-house-edge.bankroll.core :as bankroll]))

;; ============================================================================
;; ROI & Yield Calculation
;; ============================================================================

(defn calculate-roi
  "Calculate return on investment"
  [ledger]
  (let [settled (filter #(not= (:result %) :pending) ledger)
        total-staked (reduce + 0 (map :stake settled))
        total-profit (reduce + 0 (map #(or (:profit %) 0) settled))]
    (if (pos? total-staked)
      (/ total-profit total-staked)
      0.0)))

(defn calculate-yield
  "Calculate yield (profit per bet)"
  [ledger]
  (let [settled (filter #(not= (:result %) :pending) ledger)
        total-profit (reduce + 0 (map #(or (:profit %) 0) settled))
        bet-count (count settled)]
    (if (pos? bet-count)
      (/ total-profit bet-count)
      0.0)))

;; ============================================================================
;; Closing Line Value (CLV)
;; ============================================================================

(defn calculate-clv
  "Calculate closing line value
   CLV = (closing-odds / bet-odds) - 1"
  [bet-odds closing-odds]
  (- (/ closing-odds bet-odds) 1.0))

(defn average-clv
  "Calculate average CLV across all bets"
  [ledger closing-odds-map]
  (let [settled (filter #(not= (:result %) :pending) ledger)
        clv-values (keep (fn [bet]
                          (when-let [closing (get closing-odds-map (:id bet))]
                            (calculate-clv (:odds bet) closing)))
                        settled)]
    (if (seq clv-values)
      (util/mean clv-values)
      0.0)))

;; ============================================================================
;; Variance Analysis
;; ============================================================================

(defn calculate-expected-profit
  "Calculate expected profit based on EV"
  [ledger]
  (let [settled (filter #(not= (:result %) :pending) ledger)]
    (reduce + 0 (map #(* (:stake %) (:ev %)) settled))))

(defn calculate-actual-profit
  "Calculate actual profit"
  [ledger]
  (let [settled (filter #(not= (:result %) :pending) ledger)]
    (reduce + 0 (map #(or (:profit %) 0) settled))))

(defn variance-analysis
  "Analyze variance: Are results matching EV expectations?"
  [ledger]
  (let [expected (calculate-expected-profit ledger)
        actual (calculate-actual-profit ledger)
        delta (- actual expected)
        
        ;; Calculate standard deviation of results
        settled (filter #(not= (:result %) :pending) ledger)
        profits (map #(or (:profit %) 0) settled)
        std-dev (or (util/standard-deviation profits) 0.0)
        
        ;; How many standard deviations away?
        std-devs-away (if (pos? std-dev)
                       (/ delta std-dev)
                       0.0)
        
        within-expectations? (<= (Math/abs std-devs-away) 
                                (config/get-config [:performance :variance-tolerance]))]
    
    {:expected-profit (util/round expected 2)
     :actual-profit (util/round actual 2)
     :variance-delta (util/round delta 2)
     :standard-deviation (util/round std-dev 2)
     :std-devs-away (util/round std-devs-away 2)
     :within-expectations? within-expectations?
     :analysis (if within-expectations?
                "Results are within expected variance"
                (str "Results are " (util/round (Math/abs std-devs-away) 1) 
                     " standard deviations " 
                     (if (pos? std-devs-away) "above" "below") 
                     " expectations"))}))

;; ============================================================================
;; Drawdown Tracking
;; ============================================================================

(defn calculate-max-drawdown
  "Calculate maximum drawdown from peak"
  [ledger]
  (let [initial (config/initial-bankroll)
        settled (filter #(not= (:result %) :pending) ledger)
        running-balances (reductions
                         (fn [balance bet]
                           (+ balance (or (:profit bet) 0)))
                         initial
                         settled)
        
        ;; Calculate drawdowns
        drawdowns (loop [balances running-balances
                        peak initial
                        max-dd 0]
                   (if (empty? balances)
                     max-dd
                     (let [current (first balances)
                           new-peak (max peak current)
                           dd (- new-peak current)
                           new-max-dd (max max-dd dd)]
                       (recur (rest balances) new-peak new-max-dd))))
        
        max-dd-pct (if (pos? initial) (/ drawdowns initial) 0)]
    
    {:max-drawdown drawdowns
     :max-drawdown-percentage max-dd-pct
     :current-drawdown (:drawdown (bankroll/current-drawdown))
     :current-drawdown-percentage (:drawdown-percentage (bankroll/current-drawdown))}))

;; ============================================================================
;; Time-Travel Queries
;; ============================================================================

(defn time-travel-query
  "Query betting history with predicate"
  [ledger predicate]
  (filter predicate ledger))

(defn query-by-market
  "Get bets for specific market"
  [ledger market]
  (time-travel-query ledger #(= (:market %) market)))

(defn query-by-strategy
  "Get bets using specific strategy"
  [ledger strategy]
  (time-travel-query ledger #(= (:strategy %) strategy)))

(defn query-by-date-range
  "Get bets within date range"
  [ledger start-date end-date]
  (time-travel-query ledger 
                    #(and (>= (.getTime (:timestamp %)) (.getTime start-date))
                          (<= (.getTime (:timestamp %)) (.getTime end-date)))))

;; ============================================================================
;; Performance Metrics
;; ============================================================================

(defn calculate-performance-metrics
  "Calculate comprehensive performance metrics"
  [ledger]
  (let [settled (filter #(not= (:result %) :pending) ledger)
        won (filter #(= (:result %) :won) settled)
        lost (filter #(= (:result %) :lost) settled)
        void (filter #(= (:result %) :void) settled)
        
        total-staked (reduce + 0 (map :stake settled))
        total-profit (reduce + 0 (map #(or (:profit %) 0) settled))
        
        roi (calculate-roi ledger)
        yield (calculate-yield ledger)
        
        avg-odds (if (seq settled)
                  (util/mean (map :odds settled))
                  0.0)
        
        profits (map #(or (:profit %) 0) settled)
        returns (map #(/ (or (:profit %) 0) (:stake %)) settled)
        sharpe (or (util/sharpe-ratio returns) 0.0)
        
        variance (variance-analysis ledger)
        drawdown (calculate-max-drawdown ledger)]
    
    {:period {:start (when (seq settled) (:timestamp (first settled)))
              :end (when (seq settled) (:timestamp (last settled)))}
     :total-bets (count settled)
     :won (count won)
     :lost (count lost)
     :void (count void)
     :total-staked (util/round total-staked 2)
     :total-profit (util/round total-profit 2)
     :roi (util/round roi 4)
     :yield (util/round yield 2)
     :avg-odds (util/round avg-odds 2)
     :closing-line-value 0.0  ;; Would need closing odds data
     :sharpe-ratio (util/round sharpe 2)
     :max-drawdown (:max-drawdown drawdown)
     :current-drawdown (:current-drawdown drawdown)
     :variance (:variance-delta variance)
     :expected-variance (:standard-deviation variance)}))

;; ============================================================================
;; Weekly Report Generation
;; ============================================================================

(defn generate-weekly-report
  "Generate weekly performance report"
  []
  (let [ledger (bankroll/get-ledger)
        week-ago (util/hours-ago 168)
        recent-bets (query-by-date-range ledger week-ago (util/now))
        metrics (calculate-performance-metrics recent-bets)
        variance (variance-analysis recent-bets)]
    
    {:report-type :weekly
     :generated-at (util/now)
     :metrics metrics
     :variance-analysis variance
     :summary (str "Week: " (:total-bets metrics) " bets, "
                  (util/format-roi (:roi metrics)) " ROI, "
                  (util/format-currency (:total-profit metrics)) " profit")}))

;; ============================================================================
;; Public API
;; ============================================================================

(defn initialize!
  "Initialize performance oracle"
  []
  :initialized)

(defn get-performance-metrics
  "Get current performance metrics"
  []
  (calculate-performance-metrics (bankroll/get-ledger)))

(defn get-variance-report
  "Get variance analysis report"
  []
  (variance-analysis (bankroll/get-ledger)))

(defn query-history
  "Query betting history"
  [& {:keys [market strategy start-date end-date predicate]}]
  (let [ledger (bankroll/get-ledger)]
    (cond-> ledger
      market (query-by-market market)
      strategy (query-by-strategy strategy)
      (and start-date end-date) (query-by-date-range start-date end-date)
      predicate (time-travel-query predicate))))
