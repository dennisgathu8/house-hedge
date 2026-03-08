(ns the-house-edge.web.db)

;; ============================================================================
;; Default Application State
;; ============================================================================

(def default-db
  {:api-key nil
   :auth-status :locked        ;; :locked, :unlocked, :error
   :recommendations nil
   :bankroll-status nil
   :performance-metrics nil
   :variance-report nil
   :loading? false
   :error-message nil})
