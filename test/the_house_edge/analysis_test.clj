(ns the-house-edge.analysis-test
  (:require [clojure.test :refer :all]
            [the-house-edge.analysis.core :as analysis]))

(deftest test-monte-carlo-probabilities
  (testing "Monte Carlo probabilities sum to 1.0"
    (let [probs (analysis/estimate-match-probabilities 1.5 1.2)
          sum (+ (:home probs) (:draw probs) (:away probs))]
      ;; allow tiny floating point differences
      (is (> sum 0.99))
      (is (< sum 1.01)))))

(deftest test-elo-probabilities
  (testing "Elo probabilities sum to 1.0"
    (let [probs (analysis/estimate-elo-probabilities 1550 1450)
          sum (+ (:home probs) (:draw probs) (:away probs))]
      (is (> sum 0.99))
      (is (< sum 1.01))))
  (testing "Higher Elo means higher win probability"
    (let [probs (analysis/estimate-elo-probabilities 1600 1400)]
      (is (> (:home probs) (:away probs))))))

(deftest test-elo-updates
  (testing "Elo rating updates correctly after a match"
    (analysis/update-elo! "Team A" "Team B" :home)
    (is (> (analysis/get-elo "Team A") 1500))
    (is (< (analysis/get-elo "Team B") 1500))))

(deftest test-calibration
  (testing "Calibration shrinks extremes towards the mean"
    (let [calibrated (analysis/calibrate-probability 0.9)]
      (is (< calibrated 0.9)))
    (let [calibrated (analysis/calibrate-probability 0.1)]
      (is (> calibrated 0.1)))))
