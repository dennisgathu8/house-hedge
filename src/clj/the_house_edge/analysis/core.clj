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

(defn poisson-probability
  "Calculate Poisson probability for exact goals"
  [lambda k]
  (/ (* (Math/pow lambda k) (Math/exp (- lambda)))
     (reduce * (range 1 (inc k)))))

(defn estimate-match-probabilities
  "Estimate match result probabilities from expected goals"
  [home-xg away-xg]
  (let [;; Calculate probabilities for 0-5 goals for each team
        max-goals 5
        home-probs (mapv #(poisson-probability home-xg %) (range (inc max-goals)))
        away-probs (mapv #(poisson-probability away-xg %) (range (inc max-goals)))
        
        ;; Calculate result probabilities
        home-win (reduce +
                        (for [h (range (inc max-goals))
                              a (range h)]
                          (* (nth home-probs h) (nth away-probs a))))
        away-win (reduce +
                        (for [a (range (inc max-goals))
                              h (range a)]
                          (* (nth home-probs h) (nth away-probs a))))
        draw (reduce +
                    (for [g (range (inc max-goals))]
                      (* (nth home-probs g) (nth away-probs g))))]
    {:home (util/round home-win 3)
     :draw (util/round draw 3)
     :away (util/round away-win 3)}))

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
        
        ;; Adjust for form
        form-weight (config/get-config [:analysis :form-weight])
        xg-weight (config/get-config [:analysis :xg-weight])
        
        form-adjustment {:home (* (:form-score home-form) form-weight)
                        :away (* (:form-score away-form) form-weight)}
        
        ;; Combine probabilities
        combined-probs (let [total (+ (:home xg-probs) (:draw xg-probs) (:away xg-probs))]
                        {:home (/ (:home xg-probs) total)
                         :draw (/ (:draw xg-probs) total)
                         :away (/ (:away xg-probs) total)})
        
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
     :true-probability combined-probs
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
  "Get comprehensive analysis for a match"
  [match-data]
  (analyze-match match-data))
