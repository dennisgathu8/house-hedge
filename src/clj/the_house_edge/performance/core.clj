(ns the-house-edge.performance.core
  "Agent Zeta: The Performance Oracle
   ROI tracking and variance analysis"
  (:require [the-house-edge.protocol :as p]
            [the-house-edge.config :as config]
            [the-house-edge.util :as util]
            [the-house-edge.bankroll.core :as bankroll]
            [the-house-edge.analysis.core :as analysis]
            [the-house-edge.mock :as mock]
            [clojure.java.io :as io]
            [clojure.edn :as edn]))

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

;; ============================================================================
;; Historical Simulation (Baseline)
;; ============================================================================

(defn calculate-brier-score
  "Calculate Brier score for predictions.
   Lower is better. Perfect score = 0, worst = 1.0 (for single outcome)"
  [predicted-probs actual-outcome]
  (let [outcomes {:home 0, :draw 1, :away 2}
        actual-idx (get outcomes actual-outcome)
        actual-vector (case actual-idx
                        0 [1.0 0.0 0.0]
                        1 [0.0 1.0 0.0]
                        2 [0.0 0.0 1.0])]
    (reduce + (map (fn [p a]
                     (Math/pow (- p a) 2))
                   [(:home predicted-probs) (:draw predicted-probs) (:away predicted-probs)]
                   actual-vector))))

(defn backtest-matches
  "Run a backtest on historical matches"
  [matches true-prob-fn edge-threshold]
  (let [results
        (keep (fn [match-data]
                (let [match (:match match-data)
                      ;; Collect all prices for each outcome across all bookmakers
                      home-odds (map #(nth (:prices %) 0) (:odds match-data))
                      draw-odds (map #(nth (:prices %) 1) (:odds match-data))
                      away-odds (map #(nth (:prices %) 2) (:odds match-data))
                      
                      best-home (apply max home-odds)
                      best-draw (apply max draw-odds)
                      best-away (apply max away-odds)
                      
                      ;; Get model probabilities. Default to mock consensus true probs if function absent
                      probs (if true-prob-fn
                              (true-prob-fn match-data)
                              (:true-probs match-data))
                      
                      brier (calculate-brier-score probs (:actual-result match-data))
                      
                      ;; Check value against BEST available odds
                      selections [{:idx 0 :name :home :prob (:home probs) :odd best-home}
                                  {:idx 1 :name :draw :prob (:draw probs) :odd best-draw}
                                  {:idx 2 :name :away :prob (:away probs) :odd best-away}]
                      
                      value-bets (filter #(> (- (* (:prob %) (:odd %)) 1.0) edge-threshold) selections)
                      
                      ;; Pick the best value bet if any
                      best-bet (when (seq value-bets)
                                 (apply max-key #(- (* (:prob %) (:odd %)) 1.0) value-bets))
                                 
                      clv-beat (if best-bet
                                 (let [close-odds (:current-line (:line-history match-data))]
                                   (calculate-clv (:odd best-bet) (nth close-odds (:idx best-bet))))
                                 0.0)]
                  
                  ;; Learn Elo rating AFTER predictions are made
                  (analysis/update-elo! (:home-team match) (:away-team match) (:actual-result match-data))
                  
                  (when best-bet
                    (let [won? (= (:name best-bet) (:actual-result match-data))
                          stake 10.0 ;; 1u
                          profit (if won? (* stake (dec (:odd best-bet))) (- stake))
                          ev (- (* (:prob best-bet) (:odd best-bet)) 1.0)]
                      {:match-id (:id match)
                       :bet-on (:name best-bet)
                       :prob (:prob best-bet)
                       :odds (:odd best-bet)
                       :ev ev
                       :won? won?
                       :profit profit
                       :clv clv-beat
                       :brier brier}))))
              matches)
        
        bets-placed (count results)
        wins (count (filter :won? results))
        total-profit (reduce + (map :profit results))
        total-staked (* bets-placed 10.0)
        roi (if (pos? total-staked) (/ total-profit total-staked) 0.0)
        hit-rate (if (pos? bets-placed) (/ wins bets-placed) 0.0)
        avg-edge (util/mean (map :ev results))
        avg-clv (util/mean (map :clv results))
        avg-brier (util/mean (map :brier results))]
    {:period "2024-2025"
     :matches-analyzed (count matches)
     :bets-placed bets-placed
     :hit-rate hit-rate
     :roi roi
     :total-profit (util/round total-profit 2)
     :average-edge-captured avg-edge
     :average-clv-beat avg-clv
     :average-brier-score avg-brier}))

(defn generate-baseline-dataset
  [num-matches]
  (let [fixtures (repeatedly num-matches mock/generate-weekend-fixtures)]
    (->> (mapcat identity fixtures)
         (take num-matches)
         (mapv (fn [match]
                 (let [data (mock/generate-complete-match-data match)
                       outcome (util/weighted-random
                                 [:home :draw :away]
                                 [(:home (:true-probs data))
                                  (:draw (:true-probs data))
                                  (:away (:true-probs data))])]
                   (assoc data :actual-result outcome)))))))

(defn run-baseline-simulation!
  "Generate a mock historical dataset and run the simulator to get baseline metrics"
  []
  (let [matches (generate-baseline-dataset 150)
        baseline (backtest-matches matches nil 0.005)
        save-path "data/baseline-20250308.edn"]
    (io/make-parents save-path)
    (spit save-path (pr-str {:metrics baseline :raw-data matches}))
    baseline))

(defn run-poisson-simulation!
  "Run the simulator using the new Poisson-blended probabilities"
  []
  (reset! analysis/elo-ratings {})  ;; Reset memory for fresh start
  (let [save-path "data/baseline-20250308.edn"
        data (edn/read-string (slurp save-path))
        matches (:raw-data data)
        poisson-fn (fn [match-data] (:true-probability (analysis/get-match-analysis match-data)))
        ;; Ensure eager evaluation for Elo state mutation
        results (backtest-matches (vec matches) poisson-fn 0.005)]
    results))

