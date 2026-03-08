(ns the-house-edge.web.events
  "Re-frame events for The House Edge dashboard"
  (:require [re-frame.core :as re-frame]
            [the-house-edge.web.db :as db]
            [ajax.core :as ajax]
            [day8.re-frame.http-fx]))

;; ============================================================================
;; Interceptors
;; ============================================================================

(def set-auth-header
  (re-frame/->interceptor
   :id :set-auth-header
   :before (fn [context]
             (let [db (get-in context [:coeffects :db])
                   api-key (:api-key db)]
               (if api-key
                 ;; If an :http-xhrio effect is present in the queued events, update its headers
                 (if-let [http-fx (get-in context [:effects :http-xhrio])]
                   ;; Update a single http-xhrio map
                   (assoc-in context [:effects :http-xhrio :headers "X-API-Key"] api-key)
                   ;; If there's an array of :http-xhrio effects (bulk dispatch), we'd process them
                   ;; But for now, we'll configure default-headers in the ajax-request directly below
                   context)
                 context)))))

;; ============================================================================
;; Boot Events
;; ============================================================================

(re-frame/reg-event-db
 :initialize-db
 (fn [_ _]
   db/default-db))

;; ============================================================================
;; Authentication Events
;; ============================================================================

(re-frame/reg-event-fx
 :unlock-vault
 (fn [{:keys [db]} [_ key]]
   {:db (-> db
            (assoc :api-key key)
            (assoc :auth-status :unlocked)
            (assoc :error-message nil))
    :dispatch [:start-polling]}))

(re-frame/reg-event-fx
 :lock-vault
 (fn [{:keys [db]} _]
   {:db (-> db
            (assoc :api-key nil)
            (assoc :auth-status :locked)
            (assoc :recommendations nil)
            (assoc :bankroll-status nil)
            (assoc :performance-metrics nil)
            (assoc :variance-report nil))
    :dispatch [:stop-polling]}))

;; ============================================================================
;; API Fetch Events
;; ============================================================================

;; Helper to create standard HTTP requests with the API key
(defn authorized-request [db uri on-success]
  {:method :get
   :uri uri
   :headers {"X-API-Key" (:api-key db)
             "Accept" "application/json"}
   :response-format (ajax/json-response-format {:keywords? true})
   :on-success [on-success]
   :on-failure [:api-request-failed]})

(re-frame/reg-event-fx
 :fetch-dashboard-data
 (fn [{:keys [db]} _]
   (if (= :unlocked (:auth-status db))
     {:db (assoc db :loading? true :error-message nil)
      :http-xhrio [(authorized-request db "/api/bankroll" :fetch-bankroll-success)
                   (authorized-request db "/api/performance" :fetch-performance-success)
                   (authorized-request db "/api/variance" :fetch-variance-success)
                   (authorized-request db "/api/recommendations" :fetch-recommendations-success)]}
     {:db db})))

(re-frame/reg-event-fx
 :track-bet
 (fn [{:keys [db]} [_ bet-data]]
   (if (= :unlocked (:auth-status db))
     {:db db
      :http-xhrio {:method :post
                   :uri "/api/bets/track"
                   :headers {"X-API-Key" (:api-key db)}
                   :format (ajax/json-request-format)
                   :params bet-data
                   :response-format (ajax/json-response-format {:keywords? true})
                   :on-success [:track-bet-success]
                   :on-failure [:api-request-failed]}}
     {:db db})))

(re-frame/reg-event-fx
 :track-bet-success
 (fn [{:keys [db]} [_ result]]
   {:db db
    :dispatch [:fetch-dashboard-data]}))

;; ============================================================================
;; Real-Time Polling
;; ============================================================================

(defonce polling-interval (atom nil))

(re-frame/reg-event-fx
 :start-polling
 (fn [_ _]
   (when-not @polling-interval
     (reset! polling-interval
             (js/setInterval #(re-frame/dispatch [:fetch-dashboard-data]) 60000)))
   {}))

(re-frame/reg-event-fx
 :stop-polling
 (fn [_ _]
   (when @polling-interval
     (js/clearInterval @polling-interval)
     (reset! polling-interval nil))
   {}))

(re-frame/reg-event-db
 :api-request-failed
 (fn [db [_ result]]
   (if (= 401 (:status result))
     (-> db
         (assoc :auth-status :error)
         (assoc :api-key nil)
         (assoc :error-message "Invalid API Key - Access Denied"))
     (-> db
         (assoc :loading? false)
         (assoc :error-message (or (get-in result [:response :error])
                                   (:status-text result)
                                   "API request failed"))))))

;; ============================================================================
;; Success Handlers
;; ============================================================================

(re-frame/reg-event-db
 :fetch-bankroll-success
 (fn [db [_ result]]
   (-> db
       (assoc :loading? false)
       (assoc :bankroll-status (get-in result [:data])))))

(re-frame/reg-event-db
 :fetch-performance-success
 (fn [db [_ result]]
   (-> db
       (assoc :loading? false)
       (assoc :performance-metrics (get-in result [:data])))))

(re-frame/reg-event-db
 :fetch-variance-success
 (fn [db [_ result]]
   (-> db
       (assoc :loading? false)
       (assoc :variance-report (get-in result [:data])))))

(re-frame/reg-event-db
 :fetch-recommendations-success
 (fn [db [_ result]]
   (-> db
       (assoc :loading? false)
       (assoc :recommendations (get-in result [:data :recommendations])))))
