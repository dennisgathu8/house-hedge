(ns the-house-edge.web.subs
  "Re-frame subscriptions for The House Edge dashboard"
  (:require [re-frame.core :as re-frame]))

;; ============================================================================
;; Authentication Subscriptions
;; ============================================================================

(re-frame/reg-sub
 :api-key
 (fn [db _]
   (:api-key db)))

(re-frame/reg-sub
 :auth-status
 (fn [db _]
   (:auth-status db)))

(re-frame/reg-sub
 :locked?
 :<- [:auth-status]
 (fn [status _]
   (= :locked status)))

;; ============================================================================
;; UI State Subscriptions
;; ============================================================================

(re-frame/reg-sub
 :loading?
 (fn [db _]
   (:loading? db)))

(re-frame/reg-sub
 :error-message
 (fn [db _]
   (:error-message db)))

;; ============================================================================
;; Data Subscriptions
;; ============================================================================

(re-frame/reg-sub
 :recommendations
 (fn [db _]
   (:recommendations db)))

(re-frame/reg-sub
 :bankroll-status
 (fn [db _]
   (:bankroll-status db)))

(re-frame/reg-sub
 :performance-metrics
 (fn [db _]
   (:performance-metrics db)))

(re-frame/reg-sub
 :variance-report
 (fn [db _]
   (:variance-report db)))
