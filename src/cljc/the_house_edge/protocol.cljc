(ns the-house-edge.protocol
  "Shared protocol definitions for inter-agent communication.
   All data structures use EDN format for seamless Clojure/ClojureScript interop."
  (:require [schema.core :as s]))

;; ============================================================================
;; Core Schemas
;; ============================================================================

(def Match
  "Match fixture schema"
  {:id s/Uuid
   :home-team s/Str
   :away-team s/Str
   :league s/Str
   :kickoff s/Inst
   (s/optional-key :venue) s/Str
   (s/optional-key :weather) (s/enum :clear :rain :snow :wind)})

(def Market
  "Betting market types"
  (s/enum :match-result      ;; 1X2
          :asian-handicap     ;; AH
          :totals             ;; Over/Under
          :both-teams-score   ;; BTTS
          :corners            ;; Corner markets
          :cards))            ;; Card markets

(def Odds
  "Odds data from a bookmaker"
  {:bookmaker s/Str
   :match-id s/Uuid
   :market Market
   :prices [s/Num]           ;; [home draw away] or [over under] or [yes no]
   :timestamp s/Inst
   (s/optional-key :line) s/Num})  ;; For handicap/totals

(def BetResult
  "Bet settlement result"
  (s/enum :pending :won :lost :void :push))

(def StakingStrategy
  "Staking strategy types"
  (s/enum :kelly :flat :confidence))

(def Bet
  "Immutable bet record"
  {:id s/Uuid
   :match-id s/Uuid
   :market Market
   :selection s/Str          ;; "Arsenal", "Over 2.5", "Yes", etc.
   :odds s/Num
   :stake s/Num
   :strategy StakingStrategy
   :ev s/Num                 ;; Expected value (decimal)
   :timestamp s/Inst
   :result BetResult
   (s/optional-key :settled-at) s/Inst
   (s/optional-key :profit) s/Num})

;; ============================================================================
;; Agent Alpha: Odds Engine
;; ============================================================================

(def OddsUpdate
  "Real-time odds update event"
  {:match-id s/Uuid
   :market Market
   :bookmaker s/Str
   :old-prices [s/Num]
   :new-prices [s/Num]
   :timestamp s/Inst
   :movement s/Num})         ;; Price change magnitude

(def ExpectedValue
  "EV calculation result"
  {:match-id s/Uuid
   :market Market
   :selection s/Str
   :bookmaker s/Str
   :odds s/Num
   :true-probability s/Num
   :ev s/Num                 ;; (true-prob × odds) - 1
   :kelly-fraction s/Num     ;; Optimal stake size
   :timestamp s/Inst})

(def ArbitrageOpportunity
  "Cross-bookmaker arbitrage detection"
  {:match-id s/Uuid
   :market Market
   :bookmakers [s/Str]
   :selections [s/Str]
   :odds [s/Num]
   :profit-margin s/Num      ;; Guaranteed profit %
   :timestamp s/Inst})

;; ============================================================================
;; Agent Beta: Sharp Detector
;; ============================================================================

(def SharpSignalType
  "Types of sharp money signals"
  (s/enum :rlm              ;; Reverse Line Movement
          :steam            ;; Steam move
          :contrarian       ;; Contrarian indicator
          :closing-line))   ;; Closing line value

(def SharpSignal
  "Sharp money detection signal"
  {:signal-type SharpSignalType
   :match-id s/Uuid
   :market Market
   :direction s/Str          ;; "home", "away", "over", "under"
   :confidence s/Num         ;; 0.0-1.0
   :evidence [s/Any]         ;; Supporting data points
   :timestamp s/Inst
   (s/optional-key :public-percentage) s/Num
   (s/optional-key :money-percentage) s/Num})

(def LineMovement
  "Historical line movement tracking"
  {:match-id s/Uuid
   :market Market
   :bookmaker s/Str
   :opening-line [s/Num]
   :current-line [s/Num]
   :movement-history [{:timestamp s/Inst
                       :prices [s/Num]}]})

;; ============================================================================
;; Agent Gamma: Bankroll Guardian
;; ============================================================================

(def BankrollSnapshot
  "Point-in-time bankroll state"
  {:timestamp s/Inst
   :balance s/Num
   :peak-balance s/Num
   :total-staked s/Num
   :total-profit s/Num
   :bet-count s/Int
   :roi s/Num})

(def StakingDecision
  "Stake size calculation result"
  {:bet-id s/Uuid
   :strategy StakingStrategy
   :bankroll s/Num
   :recommended-stake s/Num
   :stake-percentage s/Num
   :rationale s/Str})

;; ============================================================================
;; Agent Delta: Match Analyst
;; ============================================================================

(def TeamForm
  "Team form analysis"
  {:team s/Str
   :recent-matches [{:opponent s/Str
                     :result (s/enum :win :draw :loss)
                     :goals-for s/Int
                     :goals-against s/Int
                     :xg-for s/Num
                     :xg-against s/Num
                     :date s/Inst}]
   :form-score s/Num         ;; Exponentially weighted
   :avg-xg-for s/Num
   :avg-xg-against s/Num})

(def MatchAnalysis
  "Deep match analysis result"
  {:match-id s/Uuid
   :home-form TeamForm
   :away-form TeamForm
   :true-probability {:home s/Num
                      :draw s/Num
                      :away s/Num}
   :predicted-goals {:home s/Num
                     :away s/Num}
   :key-factors [s/Str]      ;; Injuries, suspensions, etc.
   :tactical-edge (s/maybe s/Str)
   :confidence s/Num})

;; ============================================================================
;; Agent Epsilon: Slip Generator
;; ============================================================================

(def BettingSlip
  "Investment-grade betting recommendation"
  {:recommendation-id s/Uuid
   :match Match
   :market Market
   :selection s/Str
   :odds s/Num
   :ev s/Num
   :kelly-stake s/Num
   :confidence s/Num
   :rationale s/Str
   :risk-factors [s/Str]
   :sharp-signals [SharpSignal]
   :analysis MatchAnalysis
   :timestamp s/Inst})

;; ============================================================================
;; Agent Zeta: Performance Oracle
;; ============================================================================

(def PerformanceMetrics
  "Comprehensive performance analytics"
  {:period {:start s/Inst
            :end s/Inst}
   :total-bets s/Int
   :won s/Int
   :lost s/Int
   :void s/Int
   :total-staked s/Num
   :total-profit s/Num
   :roi s/Num
   :yield s/Num
   :avg-odds s/Num
   :closing-line-value s/Num
   :sharpe-ratio s/Num
   :max-drawdown s/Num
   :current-drawdown s/Num
   :variance s/Num
   :expected-variance s/Num})

(def VarianceReport
  "Variance analysis (results vs. expectations)"
  {:period {:start s/Inst
            :end s/Inst}
   :expected-profit s/Num
   :actual-profit s/Num
   :variance-delta s/Num
   :standard-deviations s/Num
   :within-expectations? s/Bool
   :analysis s/Str})

;; ============================================================================
;; System Events
;; ============================================================================

(def SystemEvent
  "System-level events for coordination"
  {:event-type (s/enum :odds-updated
                       :sharp-detected
                       :bet-placed
                       :bet-settled
                       :analysis-complete
                       :slip-generated)
   :timestamp s/Inst
   :data s/Any})

;; ============================================================================
;; Helper Functions
;; ============================================================================

(defn validate
  "Validate data against schema, throw on error"
  [schema data]
  (s/validate schema data))

(defn valid?
  "Check if data matches schema"
  [schema data]
  (nil? (s/check schema data)))

(defn explain
  "Get human-readable schema validation error"
  [schema data]
  (s/check schema data))
