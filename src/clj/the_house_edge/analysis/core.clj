(ns the-house-edge.analysis.core
  "Agent Delta: The Match Analyst
   Deep football analysis beyond surface stats"
  (:require [the-house-edge.protocol :as p]
            [the-house-edge.config :as config]
            [the-house-edge.util :as util]
            [the-house-edge.mock :as mock]))

;; ============================================================================
;; Team Form Analysis
;; ============================================================================

(defn calculate-form-score
  "Calculate exponentially weighted form score"
  [recent-matches]
  (let [decay (config/get-config [:analysis :form-decay])
        weighted-results
        (map-indexed
         (fn [idx match]
           (let [weight (Math/pow decay idx)
                 points (case (:result match)
                         :win 3.0
                         :draw 1.0
                         :loss 0.0)]
             (* weight points)))
         recent-matches)
        max-possible (* 3.0 (reduce + (map-indexed (fn [i _] (Math/pow decay i)) recent-matches)))]
    (/ (reduce + weighted-results) max-possible)))

(defn analyze-team-form
  "Comprehensive team form analysis"
  [team-form]
  (let [recent-matches (get team-form :recent-matches [])
        form-score (if (seq recent-matches) (calculate-form-score recent-matches) 0.0)
        avg-xg-for (or (util/mean (map :xg-for recent-matches)) 0.0)
        avg-xg-against (or (util/mean (map :xg-against recent-matches)) 0.0)
        wins (count (filter #(= :win (:result %)) recent-matches))
        draws (count (filter #(= :draw (:result %)) recent-matches))
        losses (count (filter #(= :loss (:result %)) recent-matches))]
    {:team (:team team-form "Unknown")
     :form-score (util/round form-score 2)
     :avg-xg-for (util/round avg-xg-for 2)
     :avg-xg-against (util/round avg-xg-against 2)
     :record {:wins wins :draws draws :losses losses}
     :recent-matches recent-matches}))

;; ============================================================================
;; xG-Based Probability Estimation
;; ============================================================================

(defn estimate-goals-from-xg
  "Estimate likely goals based on xG data"
  [avg-xg-for avg-xg-against opponent-avg-xg-for opponent-avg-xg-against]
  (let [;; Adjust for opponent defensive strength
        expected-goals-for (* avg-xg-for (/ opponent-avg-xg-against 1.5))
        expected-goals-against (* opponent-avg-xg-for (/ avg-xg-against 1.5))]
    {:expected-goals-for (util/round expected-goals-for 2)
     :expected-goals-against (util/round expected-goals-against 2)}))

(defn rand-poisson
  "Generate a random sample from a Poisson distribution with mean lambda using Knuth's algorithm"
  [lambda]
  (let [l (Math/exp (- lambda))]
    (loop [k 0
           p 1.0]
      (let [p (* p (rand))]
        (if (> p l)
          (recur (inc k) p)
          k)))))

(defn run-monte-carlo-sim
  "Run Monte Carlo simulations to derive probabilities"
  [home-xg away-xg num-sims]
  (let [results (frequencies
                 (repeatedly num-sims
                             (fn []
                               (let [hg (rand-poisson home-xg)
                                     ag (rand-poisson away-xg)]
                                 (cond
                                   (> hg ag) :home
                                   (< hg ag) :away
                                   :else :draw)))))]
    {:home (util/round (/ (get results :home 0) num-sims) 4)
     :draw (util/round (/ (get results :draw 0) num-sims) 4)
     :away (util/round (/ (get results :away 0) num-sims) 4)}))

(defn estimate-match-probabilities
  "Estimate match result probabilities from expected goals using Monte-Carlo"
  [home-xg away-xg]
  (run-monte-carlo-sim home-xg away-xg 10000))

;; ============================================================================
;; Dynamic Elo Ratings
;; ============================================================================

(defonce elo-ratings (atom {}))

(defn get-elo
  "Get Elo rating for a team, defaulting to 1500"
  [team]
  (get @elo-ratings team 1500))

(defn estimate-elo-probabilities
  "Estimate match probabilities based on Elo ratings using 3-way ordered logit"
  [home-elo away-elo]
  (let [home-adv 100
        elo-diff (+ (- home-elo away-elo) home-adv)
        ;; Ordered logit parameters (approximated for football)
        draw-margin 70
        
        home-win-prob (/ 1.0 (+ 1.0 (Math/pow 10 (/ (- draw-margin elo-diff) 400.0))))
        away-not-lose-prob (/ 1.0 (+ 1.0 (Math/pow 10 (/ (- (- draw-margin) elo-diff) 400.0))))
        
        away-win-prob (- 1.0 away-not-lose-prob)
        draw-prob (- away-not-lose-prob home-win-prob)
        
        ;; Fallback protection for extreme rating diffs
        h-prob (max 0.05 (min 0.90 home-win-prob))
        a-prob (max 0.05 (min 0.90 away-win-prob))
        d-prob (max 0.05 (min 0.40 draw-prob))
        
        total (+ h-prob a-prob d-prob)]
    {:home (util/round (/ h-prob total) 4)
     :draw (util/round (/ d-prob total) 4)
     :away (util/round (/ a-prob total) 4)}))

(defn update-elo!
  "Update Elo rating for teams after a match"
  [home-team away-team result]
  (let [home-elo (get-elo home-team)
        away-elo (get-elo away-team)
        home-adv 100
        elo-diff (+ (- home-elo away-elo) home-adv)
        
        expected-home (/ 1.0 (+ 1.0 (Math/pow 10 (/ (- elo-diff) 400.0))))
        expected-away (- 1.0 expected-home)
        
        actual-home (case result :home 1.0 :draw 0.5 :away 0.0)
        actual-away (case result :home 0.0 :draw 0.5 :away 1.0)
        
        k-factor 32
        
        new-home-elo (+ home-elo (* k-factor (- actual-home expected-home)))
        new-away-elo (+ away-elo (* k-factor (- actual-away expected-away)))]
    
    (swap! elo-ratings assoc home-team (util/round new-home-elo 1))
    (swap! elo-ratings assoc away-team (util/round new-away-elo 1))
    [new-home-elo new-away-elo]))

;; ============================================================================
;; Probability Calibration
;; ============================================================================

(defn calibrate-probability
  "Piecewise-linear calibration based on empirical curves
   Shrink extremes towards the mean to combat overconfidence"
  [prob]
  (cond
    (< prob 0.20) (+ prob (* (- 0.20 prob) 0.15))
    (> prob 0.80) (- prob (* (- prob 0.80) 0.15))
    :else prob))

(defn apply-calibration
  "Apply calibration to a probability distribution and re-normalize"
  [probs]
  (let [calibrated {:home (calibrate-probability (:home probs))
                    :draw (calibrate-probability (:draw probs))
                    :away (calibrate-probability (:away probs))}
        total (+ (:home calibrated) (:draw calibrated) (:away calibrated))]
    {:home (util/round (/ (:home calibrated) total) 4)
     :draw (util/round (/ (:draw calibrated) total) 4)
     :away (util/round (/ (:away calibrated) total) 4)}))

;; ============================================================================
;; Match Analysis
;; ============================================================================

(defn analyze-match
  "Deep match analysis combining form, xG, and tactical factors"
  [match-data]
  (let [home-form (analyze-team-form (:home-form match-data))
        away-form (analyze-team-form (:away-form match-data))
        
        ;; Expected goals
        goals (estimate-goals-from-xg
               (:avg-xg-for home-form)
               (:avg-xg-against home-form)
               (:avg-xg-for away-form)
               (:avg-xg-against away-form))
        
        ;; Probability estimates
        xg-probs (estimate-match-probabilities
                  (:expected-goals-for goals)
                  (:expected-goals-against goals))
                  
        elo-probs (estimate-elo-probabilities
                   (get-elo (:team home-form))
                   (get-elo (:team away-form)))
        
        ;; Adjust for form
        form-weight (config/get-config [:analysis :form-weight])
        xg-weight (config/get-config [:analysis :xg-weight])
        
        form-adjustment {:home (* (:form-score home-form) form-weight)
                        :away (* (:form-score away-form) form-weight)}
        
        ;; Fetch probabilities
        consensus-probs (:true-probs match-data)
        
        ;; Blend probabilities
        consensus-weight (config/get-config [:analysis :consensus-weight])
        poisson-weight (config/get-config [:analysis :poisson-weight])
        elo-weight (config/get-config [:analysis :elo-weight])
        
        ;; Ensure sum to 1.0 logic and normalize just in case
        total-weight (+ consensus-weight poisson-weight elo-weight)
        c-weight (/ consensus-weight total-weight)
        p-weight (/ poisson-weight total-weight)
        e-weight (/ elo-weight total-weight)
        
        blended-probs {:home (+ (* (:home consensus-probs) c-weight) 
                               (* (:home xg-probs) p-weight)
                               (* (:home elo-probs) e-weight))
                       :draw (+ (* (:draw consensus-probs) c-weight) 
                               (* (:draw xg-probs) p-weight)
                               (* (:draw elo-probs) e-weight))
                       :away (+ (* (:away consensus-probs) c-weight) 
                               (* (:away xg-probs) p-weight)
                               (* (:away elo-probs) e-weight))}
        
        ;; Apply calibration
        final-probs (if (config/get-config [:analysis :apply-calibration])
                      (apply-calibration blended-probs)
                      blended-probs)
        
        ;; Key factors
        key-factors (cond-> []
                      (> (:form-score home-form) 0.7)
                      (conj (str (:team home-form) " in excellent form"))
                      
                      (> (:form-score away-form) 0.7)
                      (conj (str (:team away-form) " in excellent form"))
                      
                      (> (:avg-xg-for home-form) 2.0)
                      (conj (str (:team home-form) " strong attack (xG " (:avg-xg-for home-form) ")"))
                      
                      (< (:avg-xg-against away-form) 1.0)
                      (conj (str (:team away-form) " solid defense (xGA " (:avg-xg-against away-form) ")")))
        
        ;; Confidence based on data quality
        confidence (util/clamp
                    (* 0.75 (+ (get home-form :form-score 0.5) (get away-form :form-score 0.5)) 0.5)
                    0.60
                    0.90)]
    
    {:match-id (get-in match-data [:match :id])
     :home-form home-form
     :away-form away-form
     :true-probability final-probs
     :predicted-goals goals
     :key-factors key-factors
     :tactical-edge nil  ;; Would require tactical data
     :confidence (util/round confidence 2)}))

;; ============================================================================
;; Public API
;; ============================================================================

(defn initialize!
  "Initialize match analyst"
  []
  :initialized)

(defn get-match-analysis
  "Get comprehensive analysis for a match.
   Handles live API data where form data may be absent."
  [match-data]
  (if (and (:home-form match-data) (:away-form match-data))
    ;; Full analysis with form data
    (analyze-match match-data)
    ;; Simplified analysis for live API data (no historical form)
    (let [true-probs (or (:true-probs match-data)
                         {:home 0.33 :draw 0.33 :away 0.33})
          ;; Derive confidence from odds spread concentration
          ;; Higher concentration = higher confidence (one clear favourite)
          max-prob (apply max (vals true-probs))
          confidence (util/clamp (* max-prob 1.1) 0.60 0.88)]
      {:match-id (get-in match-data [:match :id])
       :home-form nil
       :away-form nil
       :true-probability true-probs
       :predicted-goals {:expected-goals-for 1.5 :expected-goals-against 1.2}
       :key-factors ["Market-implied probability analysis"]
       :tactical-edge nil
       :confidence (util/round confidence 2)})))
