(ns the-house-edge.api.client
  "External API client for fetching live odds"
  (:require [clj-http.client :as http]
            [the-house-edge.config :as config]
            [taoensso.timbre :as log]
            [clojure.data.json :as json]))

(def sport-names
  {"soccer_epl" "EPL"
   "soccer_spain_la_liga" "La Liga"
   "soccer_italy_serie_a" "Serie A"
   "soccer_uefa_champs_league" "Champions League"})

(defn fetch-live-odds
  "Fetches live upcoming soccer odds from The Odds API"
  [sport-key]
  (try
    (let [api-key (config/get-config [:odds :api-key])
          base-url (config/get-config [:odds :base-url])
          url (str base-url "/sports/" sport-key "/odds/?apiKey=" api-key "&regions=us,uk,eu&markets=h2h&oddsFormat=decimal")
          response (http/get url {:as :json
                                  :throw-exceptions false})]
      (if (= 200 (:status response))
        (:body response)
        (do
          (log/error "Failed to fetch live odds. Status:" (:status response) "Body:" (:body response))
          nil)))
    (catch Exception e
      (log/error e "Exception while fetching live odds")
      nil)))

(defn- extract-bookie-prices
  "Extract home/draw/away prices from a single bookmaker entry"
  [bookie home-team away-team]
  (let [market (first (:markets bookie))
        outcomes (or (:outcomes market) [])
        home-odds (some #(when (= (:name %) home-team) (:price %)) outcomes)
        away-odds (some #(when (= (:name %) away-team) (:price %)) outcomes)
        draw-odds (some #(when (= (:name %) "Draw") (:price %)) outcomes)]
    (when (and home-odds away-odds draw-odds)
      {:bookmaker (or (:title bookie) (:key bookie))
       :home home-odds
       :draw draw-odds
       :away away-odds})))

(defn parse-odds-response
  "Translates The Odds API response into our internal match data package.
   Uses ALL bookmakers to derive consensus true probability and emits
   per-bookmaker odds entries so the EV engine can find cross-market value."
  [api-data sport-key]
  (when api-data
    (->> api-data
         (map (fn [match]
                (try
                  (let [home-team (:home_team match)
                       away-team (:away_team match)
                       bookmakers (:bookmakers match)
                       ;; Extract prices from ALL bookmakers
                       all-prices (->> bookmakers
                                       (map #(extract-bookie-prices % home-team away-team))
                                       (remove nil?))]
                    (when (seq all-prices)
                       ;; Consensus true probability: average implied probs across ALL bookmakers
                      (let [avg-home (/ (reduce + (map :home all-prices)) (count all-prices))
                            avg-draw (/ (reduce + (map :draw all-prices)) (count all-prices))
                            avg-away (/ (reduce + (map :away all-prices)) (count all-prices))
                            inv-home (/ 1.0 avg-home)
                            inv-draw (/ 1.0 avg-draw)
                            inv-away (/ 1.0 avg-away)
                            total-inv (+ inv-home inv-draw inv-away)
                            true-probs {:home (/ inv-home total-inv)
                                        :draw (/ inv-draw total-inv)
                                        :away (/ inv-away total-inv)}
                       
                            match-uuid (java.util.UUID/randomUUID)
                            match-obj {:id match-uuid
                                       :home-team home-team
                                       :away-team away-team
                                       :league (get sport-names sport-key sport-key)
                                       :kickoff (:commence_time match)}
                       
                            ;; Emit one odds entry PER bookmaker so find-best-odds works
                            odds-entries (mapv (fn [bp]
                                                {:bookmaker (:bookmaker bp)
                                                 :match-id match-uuid
                                                 :market :match-result
                                                 :prices [(:home bp) (:draw bp) (:away bp)]
                                                 :timestamp (java.util.Date.)})
                                              all-prices)]
                   
                        {:match match-obj
                         :true-probs true-probs
                         :odds odds-entries
                         :sharp-signal nil
                         :line-history {:opening-line [avg-home avg-draw avg-away]
                                        :current-line [(:home (last all-prices))
                                                       (:draw (last all-prices))
                                                       (:away (last all-prices))]}})))
                  (catch Exception _ nil))))
         (remove nil?)
         vec)))
