(ns the-house-edge.util
  "Shared utility functions for The House Edge platform"
  (:require [clojure.string :as str]))

;; ============================================================================
;; UUID Generation
;; ============================================================================

(defn uuid
  "Generate a random UUID"
  []
  #?(:clj (java.util.UUID/randomUUID)
     :cljs (random-uuid)))

;; ============================================================================
;; Time Utilities
;; ============================================================================

(defn now
  "Get current timestamp"
  []
  #?(:clj (java.util.Date.)
     :cljs (js/Date.)))

(defn hours-ago
  "Get timestamp N hours ago"
  [n]
  #?(:clj (java.util.Date. (- (.getTime (java.util.Date.)) (* n 60 60 1000)))
     :cljs (js/Date. (- (.getTime (js/Date.)) (* n 60 60 1000)))))

(defn days-from-now
  "Get timestamp N days from now"
  [n]
  #?(:clj (java.util.Date. (+ (.getTime (java.util.Date.)) (* n 24 60 60 1000)))
     :cljs (js/Date. (+ (.getTime (js/Date.)) (* n 24 60 60 1000)))))

(defn format-timestamp
  "Format timestamp for display"
  [ts]
  #?(:clj (str ts)
     :cljs (.toISOString ts)))

;; ============================================================================
;; Math Utilities
;; ============================================================================

(defn round
  "Round number to N decimal places"
  [n decimals]
  #?(:clj (let [factor (Math/pow 10 decimals)]
            (/ (Math/round (* n factor)) factor))
     :cljs (let [factor (js/Math.pow 10 decimals)]
             (/ (js/Math.round (* n factor)) factor))))

(defn clamp
  "Clamp value between min and max"
  [value min-val max-val]
  (max min-val (min max-val value)))

(defn percentage
  "Convert decimal to percentage string"
  [n]
  (str (round (* n 100) 2) "%"))

;; ============================================================================
;; Probability Utilities
;; ============================================================================

(defn odds-to-probability
  "Convert decimal odds to implied probability"
  [odds]
  (/ 1.0 odds))

(defn probability-to-odds
  "Convert probability to decimal odds"
  [prob]
  (/ 1.0 prob))

(defn remove-margin
  "Remove bookmaker margin from odds to get true probabilities"
  [odds-vector]
  (let [total-prob (reduce + (map odds-to-probability odds-vector))
        margin (- total-prob 1.0)
        true-probs (map #(/ (odds-to-probability %) total-prob) odds-vector)]
    true-probs))

(defn calculate-ev
  "Calculate expected value: (true-probability × odds) - 1"
  [true-prob odds]
  (- (* true-prob odds) 1.0))

(defn kelly-criterion
  "Calculate Kelly Criterion stake size"
  [bankroll edge odds fraction]
  (let [kelly-stake (* bankroll (/ edge (dec odds)))
        fractional-kelly (* kelly-stake fraction)]
    (max 0 fractional-kelly)))

;; ============================================================================
;; Statistical Utilities
;; ============================================================================

(defn mean
  "Calculate arithmetic mean"
  [numbers]
  (when (seq numbers)
    (/ (reduce + numbers) (count numbers))))

(defn variance
  "Calculate variance"
  [numbers]
  (when (seq numbers)
    (let [avg (mean numbers)
          squared-diffs (map #(Math/pow (- % avg) 2) numbers)]
      (mean squared-diffs))))

(defn standard-deviation
  "Calculate standard deviation"
  [numbers]
  (when (seq numbers)
    (Math/sqrt (variance numbers))))

(defn sharpe-ratio
  "Calculate Sharpe ratio (returns / volatility)"
  [returns]
  (when (seq returns)
    (let [avg-return (mean returns)
          std-dev (standard-deviation returns)]
      (if (pos? std-dev)
        (/ avg-return std-dev)
        0.0))))

;; ============================================================================
;; Collection Utilities
;; ============================================================================

(defn find-by
  "Find first item in collection where (pred item) is true"
  [pred coll]
  (first (filter pred coll)))

(defn index-by
  "Index collection by key function"
  [key-fn coll]
  (into {} (map (fn [item] [(key-fn item) item]) coll)))

(defn group-by-key
  "Group collection by key"
  [key-fn coll]
  (reduce (fn [acc item]
            (let [k (key-fn item)]
              (update acc k (fnil conj []) item)))
          {}
          coll))

;; ============================================================================
;; Validation Utilities
;; ============================================================================

(defn valid-odds?
  "Check if odds are valid (> 1.0)"
  [odds]
  (and (number? odds) (> odds 1.0)))

(defn valid-probability?
  "Check if probability is valid (0.0-1.0)"
  [prob]
  (and (number? prob) (>= prob 0.0) (<= prob 1.0)))

(defn valid-stake?
  "Check if stake is valid (> 0)"
  [stake]
  (and (number? stake) (pos? stake)))

;; ============================================================================
;; Formatting Utilities
;; ============================================================================

(defn format-currency
  "Format number as currency"
  [amount]
  (str "$" (round amount 2)))

(defn format-odds
  "Format odds for display"
  [odds]
  (round odds 2))

(defn format-ev
  "Format EV as percentage"
  [ev]
  (str (if (pos? ev) "+" "") (percentage ev)))

(defn format-roi
  "Format ROI as percentage"
  [roi]
  (str (if (pos? roi) "+" "") (percentage roi)))

;; ============================================================================
;; Random Utilities (for mock data)
;; ============================================================================

(defn random-between
  "Generate random number between min and max"
  [min-val max-val]
  (+ min-val (* (rand) (- max-val min-val))))

(defn random-int-between
  "Generate random integer between min and max (inclusive)"
  [min-val max-val]
  (+ min-val (rand-int (inc (- max-val min-val)))))

(defn random-choice
  "Pick random element from collection"
  [coll]
  (nth coll (rand-int (count coll))))

(defn random-boolean
  "Generate random boolean with given probability of true"
  [prob]
  (< (rand) prob))

(defn weighted-random
  "Pick random element based on weights"
  [items weights]
  (let [total (reduce + weights)
        r (* (rand) total)]
    (loop [items items
           weights weights
           acc 0]
      (if (empty? items)
        (last items)
        (let [new-acc (+ acc (first weights))]
          (if (< r new-acc)
            (first items)
            (recur (rest items) (rest weights) new-acc)))))))
