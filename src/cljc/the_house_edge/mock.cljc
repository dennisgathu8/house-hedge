(ns the-house-edge.mock
  "Mock data generators for MVP demonstration"
  (:require [the-house-edge.protocol :as p]
            [the-house-edge.config :as config]
            [the-house-edge.util :as util]))

;; ============================================================================
;; Match Generation
;; ============================================================================

(defn generate-match
  "Generate a mock match fixture"
  [league teams]
  (let [home-team (util/random-choice teams)
        away-team (util/random-choice (remove #{home-team} teams))
        kickoff (util/days-from-now (util/random-int-between 0 7))]
    {:id (util/uuid)
     :home-team home-team
     :away-team away-team
     :league (:id league)
     :kickoff kickoff
     :venue (str home-team " Stadium")
     :weather (util/random-choice [:clear :clear :clear :rain :wind])}))

(defn generate-weekend-fixtures
  "Generate fixtures for upcoming weekend"
  []
  (mapcat (fn [league]
            (let [teams (get config/mock-teams (keyword (:id league)))
                  num-matches (util/random-int-between 8 12)]
              (repeatedly num-matches #(generate-match league teams))))
          config/mock-leagues))

;; ============================================================================
;; Odds Generation
;; ============================================================================

(defn generate-true-probabilities
  "Generate realistic true probabilities for a match"
  [home-team away-team]
  ;; Simple model: slight home advantage
  (let [base-home (util/random-between 0.25 0.50)
        base-away (util/random-between 0.20 0.40)
        base-draw (- 1.0 base-home base-away)
        ;; Normalize
        total (+ base-home base-draw base-away)
        home (/ base-home total)
        draw (/ base-draw total)
        away (/ base-away total)]
    {:home home :draw draw :away away}))

(defn add-bookmaker-margin
  "Add bookmaker margin to true probabilities"
  [true-probs margin]
  (let [factor (+ 1.0 margin)]
    {:home (* (:home true-probs) factor)
     :draw (* (:draw true-probs) factor)
     :away (* (:away true-probs) factor)}))

(defn probabilities-to-odds
  "Convert probabilities to decimal odds"
  [probs]
  [(util/probability-to-odds (:home probs))
   (util/probability-to-odds (:draw probs))
   (util/probability-to-odds (:away probs))])

(defn generate-odds
  "Generate odds for a match from a specific bookmaker"
  [match-id bookmaker-key true-probs]
  (let [bookmaker (get config/mock-bookmakers bookmaker-key)
        margin (:margin bookmaker)
        probs-with-margin (add-bookmaker-margin true-probs margin)
        odds (probabilities-to-odds probs-with-margin)
        ;; Add some random variation
        varied-odds (map #(* % (util/random-between 0.98 1.02)) odds)]
    {:bookmaker (:name bookmaker)
     :match-id match-id
     :market :match-result
     :prices (mapv #(util/round % 2) varied-odds)
     :timestamp (util/now)}))

(defn generate-all-odds
  "Generate odds from all bookmakers for a match"
  [match]
  (let [true-probs (generate-true-probabilities (:home-team match) (:away-team match))]
    {:match match
     :true-probs true-probs
     :odds (mapv #(generate-odds (:id match) % true-probs)
                 (keys config/mock-bookmakers))}))

;; ============================================================================
;; Line Movement Simulation
;; ============================================================================

(defn simulate-line-movement
  "Simulate realistic line movement over time"
  [initial-odds movement-type]
  (case movement-type
    :sharp-on-home
    ;; Sharp money on home team - odds shorten despite public on away
    (update initial-odds 0 #(* % 0.95))
    
    :sharp-on-away
    ;; Sharp money on away team
    (update initial-odds 2 #(* % 0.95))
    
    :steam-move
    ;; Synchronized movement across books
    (mapv #(* % (util/random-between 0.92 0.96)) initial-odds)
    
    :public-push
    ;; Public money causing obvious movement
    (update initial-odds (util/random-int-between 0 2) #(* % 0.97))
    
    :stable
    ;; Minor fluctuations only
    (mapv #(* % (util/random-between 0.99 1.01)) initial-odds)))

(defn generate-line-history
  "Generate historical line movement for a match"
  [match-id bookmaker initial-odds]
  (let [movement-type (util/weighted-random
                       [:sharp-on-home :sharp-on-away :steam-move :public-push :stable]
                       [0.15 0.15 0.10 0.25 0.35])
        hours-back 48
        intervals (range hours-back 0 -2)]
    {:match-id match-id
     :market :match-result
     :bookmaker bookmaker
     :opening-line initial-odds
     :current-line (simulate-line-movement initial-odds movement-type)
     :movement-history
     (mapv (fn [hours-ago]
             {:timestamp (util/hours-ago hours-ago)
              :prices (if (< hours-ago 12)
                       (simulate-line-movement initial-odds movement-type)
                       initial-odds)})
           intervals)}))

;; ============================================================================
;; xG Data Generation
;; ============================================================================

(defn generate-xg-match
  "Generate xG data for a single match"
  [team opponent]
  (let [goals-for (util/random-int-between 0 4)
        goals-against (util/random-int-between 0 3)
        xg-for (util/random-between 0.5 3.5)
        xg-against (util/random-between 0.5 2.5)
        result (cond
                 (> goals-for goals-against) :win
                 (< goals-for goals-against) :loss
                 :else :draw)]
    {:opponent opponent
     :result result
     :goals-for goals-for
     :goals-against goals-against
     :xg-for (util/round xg-for 2)
     :xg-against (util/round xg-against 2)
     :date (util/days-from-now (- (util/random-int-between 1 30)))}))

(defn generate-team-form
  "Generate recent form for a team"
  [team league]
  (let [teams (get config/mock-teams (keyword league))
        opponents (take 10 (repeatedly #(util/random-choice (remove #{team} teams))))
        recent-matches (mapv #(generate-xg-match team %) opponents)
        wins (count (filter #(= :win (:result %)) recent-matches))
        form-score (/ wins 10.0)
        avg-xg-for (util/mean (map :xg-for recent-matches))
        avg-xg-against (util/mean (map :xg-against recent-matches))]
    {:team team
     :recent-matches recent-matches
     :form-score (util/round form-score 2)
     :avg-xg-for (util/round avg-xg-for 2)
     :avg-xg-against (util/round avg-xg-against 2)}))

;; ============================================================================
;; Sharp Signal Generation
;; ============================================================================

(defn generate-sharp-signal
  "Generate a sharp money signal"
  [match-id odds-data]
  (let [signal-type (util/random-choice [:rlm :steam :contrarian])
        direction (util/random-choice ["home" "away"])
        confidence (util/random-between 0.65 0.95)]
    {:signal-type signal-type
     :match-id match-id
     :market :match-result
     :direction direction
     :confidence (util/round confidence 2)
     :evidence [(str "Line movement: " (util/percentage (util/random-between 0.02 0.08)))
                (str "Public: " (util/random-int-between 55 75) "% on " 
                     (if (= direction "home") "away" "home"))
                (str "Money: " (util/random-int-between 60 80) "% on " direction)]
     :timestamp (util/now)
     :public-percentage (util/round (util/random-between 0.4 0.7) 2)
     :money-percentage (util/round (util/random-between 0.55 0.75) 2)}))

;; ============================================================================
;; Complete Match Package
;; ============================================================================

(defn generate-complete-match-data
  "Generate complete data package for a match (odds, analysis, signals)"
  [match]
  (let [odds-data (generate-all-odds match)
        true-probs (:true-probs odds-data)
        pinnacle-odds (first (:odds odds-data))
        line-history (generate-line-history (:id match) "Pinnacle" (:prices pinnacle-odds))
        home-form (generate-team-form (:home-team match) (:league match))
        away-form (generate-team-form (:away-team match) (:league match))
        ;; 30% chance of sharp signal
        sharp-signal (when (util/random-boolean 0.3)
                      (generate-sharp-signal (:id match) odds-data))]
    {:match match
     :odds (:odds odds-data)
     :true-probs true-probs
     :line-history line-history
     :home-form home-form
     :away-form away-form
     :sharp-signal sharp-signal}))

;; ============================================================================
;; Batch Generation
;; ============================================================================

(defn generate-weekend-slate
  "Generate complete data for weekend fixtures"
  []
  (let [fixtures (generate-weekend-fixtures)]
    (mapv generate-complete-match-data fixtures)))

(defn generate-value-bets
  "Filter matches with +EV > threshold"
  [matches min-ev]
  (filter (fn [match-data]
            (let [true-probs (:true-probs match-data)
                  best-odds (apply max (map #(apply max (:prices %)) (:odds match-data)))
                  best-prob (apply max (vals true-probs))
                  ev (util/calculate-ev best-prob best-odds)]
              (> ev min-ev)))
          matches))
