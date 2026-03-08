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

(defn parse-odds-response
  "Translates The Odds API response into our internal match data package"
  [api-data sport-key]
  (when api-data
    (mapv (fn [match]
            (let [bookmakers (:bookmakers match)
                  first-bookie (first bookmakers)
                  market (first (:markets first-bookie))
                  outcomes (:outcomes market)
                  home-odds (some #(when (= (:name %) (:home_team match)) (:price %)) outcomes)
                  away-odds (some #(when (= (:name %) (:away_team match)) (:price %)) outcomes)
                  draw-odds (some #(when (= (:name %) "Draw") (:price %)) outcomes)
                  ;; Mock true probabilities based on live odds (removing margin)
                  ;; Since this is Phase 2, we generate approximate implied probs
                  inv-home (/ 1.0 (or home-odds 2.0))
                  inv-draw (/ 1.0 (or draw-odds 3.0))
                  inv-away (/ 1.0 (or away-odds 3.0))
                  total-inv (+ inv-home inv-draw inv-away)
                  true-probs {:home (/ inv-home total-inv) 
                              :draw (/ inv-draw total-inv) 
                              :away (/ inv-away total-inv)}
                  prices [(or home-odds 2.0) (or draw-odds 3.0) (or away-odds 3.0)]
                  match-uuid (java.util.UUID/randomUUID)
                  match-obj {:id match-uuid
                             :home-team (:home_team match)
                             :away-team (:away_team match)
                             :league (get sport-names sport-key sport-key)}]
              
              {:match match-obj
               :true-probs true-probs
               :odds [{:bookmaker "Pinnacle"
                       :match-id match-uuid
                       :market :match-result
                       :prices prices
                       :timestamp (java.util.Date.)}]
               :sharp-signal nil
               :line-history {:opening-line prices :current-line prices}}))
          api-data)))
