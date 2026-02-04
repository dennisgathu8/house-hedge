(ns the-house-edge.slips.core
  "Agent Epsilon: The Slip Generator
   Investment-grade recommendation output"
  (:require [the-house-edge.protocol :as p]
            [the-house-edge.config :as config]
            [the-house-edge.util :as util]
            [the-house-edge.odds.core :as odds]
            [the-house-edge.sharp.core :as sharp]
            [the-house-edge.bankroll.core :as bankroll]
            [the-house-edge.analysis.core :as analysis]))

;; ============================================================================
;; Recommendation Generation
;; ============================================================================

(defn generate-rationale
  "Generate quantified investment rationale"
  [ev confidence sharp-signals analysis]
  (let [parts [(str "Expected value: " (util/format-ev ev))
               (str "Confidence: " (util/percentage confidence))]]
    (str (clojure.string/join ". " parts) ".")))

(defn identify-risk-factors
  "Identify risk factors for a bet"
  [match analysis]
  (let [factors []]
    (cond-> factors
      (= (:weather match) :rain)
      (conj "Adverse weather conditions")
      
      (< (:confidence analysis) 0.75)
      (conj "Limited data confidence")
      
      (> (get-in analysis [:predicted-goals :expected-goals-for]) 3.0)
      (conj "High-scoring match expected (increased variance)")
      
      true
      (conj "Standard betting risk applies"))))

(defn create-betting-slip
  "Create investment-grade betting slip"
  [match-data ev-result analysis sharp-signals]
  (let [match (:match match-data)
        staking-decision (bankroll/make-staking-decision
                         (:match-id ev-result)
                         (:market ev-result)
                         (:selection ev-result)
                         (:odds ev-result)
                         (:ev ev-result)
                         (:confidence analysis))
        
        rationale (generate-rationale
                   (:ev ev-result)
                   (:confidence analysis)
                   sharp-signals
                   analysis)
        
        risk-factors (identify-risk-factors match analysis)]
    
    {:recommendation-id (util/uuid)
     :match match
     :market (:market ev-result)
     :selection (:selection ev-result)
     :odds (:odds ev-result)
     :ev (:ev ev-result)
     :kelly-stake (:recommended-stake staking-decision)
     :confidence (:confidence analysis)
     :rationale rationale
     :risk-factors risk-factors
     :sharp-signals (or sharp-signals [])
     :analysis analysis
     :timestamp (util/now)}))

;; ============================================================================
;; Slip Filtering
;; ============================================================================

(defn meets-criteria?
  "Check if slip meets recommendation criteria"
  [slip]
  (let [min-ev (config/min-ev)
        min-conf (config/min-confidence)]
    (and (>= (:ev slip) min-ev)
         (>= (:confidence slip) min-conf))))

(defn rank-slips
  "Rank slips by quality (EV × Confidence)"
  [slips]
  (sort-by #(* (:ev %) (:confidence %)) > slips))

;; ============================================================================
;; Multi-Market Coverage
;; ============================================================================

(defn generate-slips-for-match
  "Generate slips for all markets on a match"
  [match-data]
  (let [analysis (analysis/get-match-analysis match-data)
        ev-analysis (odds/analyze-match-ev match-data)
        sharp-analysis (sharp/detect-sharp-money match-data)
        sharp-signals (get sharp-analysis :signals [])
        
        ;; Generate slips for value bets
        slips (mapv #(create-betting-slip
                     match-data
                     %
                     analysis
                     sharp-signals)
                   (:value-bets ev-analysis))
        
        ;; Filter by criteria
        qualified-slips (filter meets-criteria? slips)]
    
    qualified-slips))

;; ============================================================================
;; Batch Processing
;; ============================================================================

(defn generate-weekend-slips
  "Generate all betting slips for weekend fixtures"
  []
  (let [fixtures (the-house-edge.mock/generate-weekend-slate)
        all-slips (mapcat generate-slips-for-match fixtures)
        ranked-slips (rank-slips all-slips)
        max-slips (config/get-config [:slips :max-daily-slips])
        top-slips (take max-slips ranked-slips)]
    
    {:total-matches (count fixtures)
     :total-slips (count all-slips)
     :recommended-slips (count top-slips)
     :slips top-slips}))

;; ============================================================================
;; Slip Formatting
;; ============================================================================

(defn format-slip-for-display
  "Format slip for user-friendly display"
  [slip]
  {:match (str (get-in slip [:match :home-team]) " vs " 
              (get-in slip [:match :away-team]))
   :league (get-in slip [:match :league])
   :kickoff (util/format-timestamp (get-in slip [:match :kickoff]))
   :selection (:selection slip)
   :odds (util/format-odds (:odds slip))
   :stake (util/format-currency (:kelly-stake slip))
   :ev (util/format-ev (:ev slip))
   :confidence (util/percentage (:confidence slip))
   :rationale (:rationale slip)
   :risk-factors (:risk-factors slip)
   :sharp-signals (count (:sharp-signals slip))})

;; ============================================================================
;; Public API
;; ============================================================================

(defn initialize!
  "Initialize slip generator"
  []
  :initialized)

(defn get-recommendations
  "Get current betting recommendations"
  []
  (generate-weekend-slips))

(defn get-slip-by-id
  "Get specific slip by ID"
  [slip-id]
  ;; Would query from storage in production
  nil)
