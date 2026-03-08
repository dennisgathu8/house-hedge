(ns the-house-edge.web.views
  "Reagent UI components for The House Edge dashboard"
  (:require [re-frame.core :as re-frame]
            [reagent.core :as r]
            [the-house-edge.web.subs :as subs]
            [the-house-edge.web.events :as events]))

;; ============================================================================
;; Components: Authentication
;; ============================================================================

(defn unlock-screen []
  (let [input-val (r/atom "")
        error-msg (re-frame/subscribe [:error-message])]
    (fn []
      [:div.vault-container
       {:style {:display "flex"
                :flex-direction "column"
                :align-items "center"
                :justify-content "center"
                :min-height "60vh"
                :text-align "center"}}
       [:div.card {:style {:max-width "500px" :width "100%" :padding "40px"}}
        [:h2 {:style {:font-size "2em" :margin-bottom "10px"}} "🔐 Access The Vault"]
        [:p {:style {:opacity "0.8" :margin-bottom "30px"}} 
         "Enter your API key to decrypt and load the intelligence engine."]
        
        [:form {:on-submit (fn [e]
                             (.preventDefault e)
                             (when (not-empty @input-val)
                               (re-frame/dispatch [:unlock-vault @input-val])
                               (re-frame/dispatch [:fetch-dashboard-data])))}
         [:div {:style {:margin-bottom "20px"}}
          [:input {:type "password"
                   :placeholder "Paste your API Key..."
                   :value @input-val
                   :on-change #(reset! input-val (-> % .-target .-value))
                   :style {:width "100%"
                           :padding "15px"
                           :border-radius "8px"
                           :border "1px solid rgba(255,255,255,0.3)"
                           :background "rgba(0,0,0,0.3)"
                           :color "white"
                           :font-size "1.1em"
                           :text-align "center"
                           :outline "none"}}]]
         
         (when @error-msg
           [:div.error {:style {:margin-bottom "20px"}} @error-msg])
         
         [:button {:type "submit"
                   :style {:width "100%"
                           :padding "15px"
                           :font-size "1.2em"
                           :font-weight "bold"
                           :background "#4ade80"
                           :color "#000"
                           :border "none"}} 
          "Unlock Dashboard"]]]])))

;; ============================================================================
;; Components: Dashboard
;; ============================================================================

(defn metric-pill [{:keys [label value highlighted? positive? negative?]}]
  [:div.metric
   [:span.metric-label label]
   [:span.metric-value 
    {:class (cond positive? "positive" negative? "negative" :else "")}
    value]])

(defn dashboard-widgets []
  (let [bankroll @(re-frame/subscribe [:bankroll-status])
        performance @(re-frame/subscribe [:performance-metrics])
        variance @(re-frame/subscribe [:variance-report])
        loading? @(re-frame/subscribe [:loading?])]
    [:div.dashboard
     ;; Bankroll Card
     [:div.card
      [:h2 "📊 Bankroll"]
      (if loading?
        [:div.loading "Loading..."]
        [:div
         [metric-pill {:label "Current Balance" :value (str "$" (js/parseFloat (or (:current-bankroll bankroll) 0)))}]
         [metric-pill {:label "Peak Balance" :value (str "$" (js/parseFloat (get-in bankroll [:snapshot :peak-balance]))) }]
         [metric-pill {:label "ROI" 
                       :value (str (* 100 (get-in bankroll [:snapshot :roi] 0)) "%")
                       :positive? (>= (get-in bankroll [:snapshot :roi] 0) 0)
                       :negative? (< (get-in bankroll [:snapshot :roi] 0) 0)}]
         [metric-pill {:label "Risk Status" :value (str (get-in bankroll [:risk-alert :alert]))}]])]
     
     ;; Performance Card
     [:div.card
      [:h2 "📈 Performance"]
      (if loading?
        [:div.loading "Loading..."]
        [:div
         [metric-pill {:label "Total Bets" :value (or (:total-bets performance) 0)}]
         [metric-pill {:label "Win Rate" :value (let [t (:total-bets performance 0)
                                                      w (:won performance 0)]
                                                  (if (pos? t) (str (.toFixed (* 100 (/ w t)) 1) "%") "0%"))}]
         [metric-pill {:label "Sharpe Ratio" :value (.toFixed (or (:sharpe-ratio performance) 0) 2)}]
         [metric-pill {:label "Avg Odds" :value (.toFixed (or (:avg-odds performance) 0) 2)}]])]
     
     ;; Variance Card
     [:div.card
      [:h2 "⚡ Variance"]
      (if loading?
        [:div.loading "Loading..."]
        [:div
         [metric-pill {:label "Expected Profit" :value (str "$" (.toFixed (or (:expected-profit variance) 0) 2))}]
         [metric-pill {:label "Actual Profit" 
                       :value (str "$" (.toFixed (or (:actual-profit variance) 0) 2))
                       :positive? (>= (or (:actual-profit variance) 0) 0)
                       :negative? (< (or (:actual-profit variance) 0) 0)}]
         [metric-pill {:label "Std Devs Away" :value (str (.toFixed (or (:std-devs-away variance) 0) 2) "σ")}]
         [metric-pill {:label "Status" 
                       :value (if (:within-expectations variance) "✓ Normal" "⚠ Outlier")
                       :positive? (:within-expectations variance)
                       :negative? (not (:within-expectations variance))}]])]]))

(defn recommendations-list []
  (let [recs @(re-frame/subscribe [:recommendations])
        loading? @(re-frame/subscribe [:loading?])]
    [:div.recommendations
     [:div {:style {:display "flex" :justify-content "space-between" :align-items "center" :margin-bottom "20px"}}
      [:h2 "💎 Investment-Grade Recommendations"]
      [:button {:on-click #(re-frame/dispatch [:fetch-dashboard-data])} "🔄 Refresh"]]
     
     (cond
       loading? [:div.loading "Scanning markets for value bets..."]
       (empty? recs) [:div {:style {:text-align "center" :padding "40px" :opacity "0.7"}} 
                      "No value bets found at the moment. Check back later!"]
       :else
       [:div
        (for [[idx rec] (map-indexed vector recs)]
          ^{:key idx}
          [:div.rec-item
           [:div.rec-header
            [:div.match-title (:match rec)]
            [:div.ev-badge "+" (:ev rec)]]
           [:div {:style {:opacity "0.8" :margin "5px 0"}}
            (:league rec) " • " (:kickoff rec)]
           [:div.rec-details
            [:div.detail-item
             [:div.detail-label "Selection"]
             [:div.detail-value (:selection rec)]]
            [:div.detail-item
             [:div.detail-label "Avg Odds"]
             [:div.detail-value (:odds rec)]]
            [:div.detail-item
             [:div.detail-label "Rec. Stake"]
             [:div.detail-value (:stake rec)]]
            [:div.detail-item
             [:div.detail-label "Confidence"]
             [:div.detail-value (:confidence rec)]]]
           [:div.rationale
            [:strong "Rationale: "] (:rationale rec)
            (when (pos? (:sharp_signals rec 0))
              [:span [:br] [:strong "⚡ " (:sharp_signals rec) " sharp signal(s) detected"]])]
           
           ;; Interactive "Track Bet" action
           [:div {:style {:margin-top "15px" :text-align "right"}}
            [:button {:style {:background "rgba(74, 222, 128, 0.2)"
                              :border "1px solid #4ade80"
                              :color "#4ade80"
                              :font-size "0.9em"
                              :padding "8px 20px"
                              :cursor "pointer"}
                      :on-click #(re-frame/dispatch [:track-bet rec])}
             "🎯 Track Bet"]]])])]))

;; ============================================================================
;; Main Entry View
;; ============================================================================

(defn main-panel []
  (let [locked? @(re-frame/subscribe [:locked?])]
    [:div.container
     [:header
      [:h1 "🎯 THE HOUSE EDGE"]
      [:p.tagline "\"Every bet is a fact. Every edge is data. Every decision is forkable.\""]
      
      (when-not locked?
        [:div {:style {:position "absolute" :top "20px" :right "20px"}}
         [:button {:on-click #(re-frame/dispatch [:lock-vault])
                   :style {:background "transparent"
                           :border "1px solid rgba(255,255,255,0.3)"
                           :padding "8px 15px"
                           :font-size "0.8m"}} 
          "🔒 Lock Vault"]])]
     
     (if locked?
       [unlock-screen]
       [:div
        [dashboard-widgets]
        [recommendations-list]])]))
