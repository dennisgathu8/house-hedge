(ns the-house-edge.sharp.core
  "Agent Beta: The Sharp Detector
   Sharp money detection and reverse line movement analysis"
  (:require [the-house-edge.protocol :as p]
            [the-house-edge.config :as config]
            [the-house-edge.util :as util]
            [the-house-edge.odds.core :as odds]))

;; ============================================================================
;; State Management
;; ============================================================================

;; Atom storing all detected sharp signals
(defonce sharp-signals (atom []))

;; ============================================================================
;; Reverse Line Movement (RLM) Detection
;; ============================================================================

(defn detect-rlm
  "Detect Reverse Line Movement
   RLM occurs when: Public bets heavily on A, but line moves toward B"
  [line-history public-percentages]
  (let [opening-line (:opening-line line-history)
        current-line (:current-line line-history)
        movement (odds/calculate-line-movement opening-line current-line)
        
        ;; Check each selection for RLM
        rlm-signals
        (when (and (seq opening-line) (seq current-line))
          (for [i (range (min (count opening-line) (count current-line)))]
            (let [line-moved-down? (< (nth current-line i) (nth opening-line i))
                public-pct (get public-percentages i 0.5)
                public-heavy? (> public-pct 0.55)
                
                ;; RLM: Public heavy on selection, but odds shortening (line moving down)
                is-rlm? (and public-heavy? line-moved-down?)
                movement-magnitude (Math/abs (- (nth current-line i) (nth opening-line i)))
                threshold (config/get-config [:sharp :rlm-threshold])]
            
            (when (and is-rlm? (> movement-magnitude threshold))
              {:selection-index i
               :public-percentage public-pct
               :line-movement movement-magnitude
               :direction (if (= i 0) "home" (if (= i 2) "away" "draw"))})) ))]
    
    (filter some? rlm-signals)))

(defn create-rlm-signal
  "Create RLM sharp signal"
  [match-id market rlm-data]
  (let [confidence (util/clamp 
                    (* 0.7 (/ (:line-movement rlm-data) 0.1))  ;; Scale by movement
                    0.65 
                    0.95)]
    {:signal-type :rlm
     :match-id match-id
     :market market
     :direction (:direction rlm-data)
     :confidence (util/round confidence 2)
     :evidence [(str "Public: " (util/percentage (:public-percentage rlm-data)) " on opposite side")
                (str "Line movement: " (util/percentage (:line-movement rlm-data)))]
     :timestamp (util/now)
     :public-percentage (:public-percentage rlm-data)
     :money-percentage (- 1.0 (:public-percentage rlm-data))}))

;; ============================================================================
;; Steam Move Detection
;; ============================================================================

(defn detect-steam
  "Detect steam moves (synchronized line movement across multiple books)"
  [multi-book-lines]
  (when (> (count multi-book-lines) 2)
    (let [;; Get movements for each book
          movements (map (fn [line-hist]
                          (let [opening (:opening-line line-hist)
                                current (:current-line line-hist)]
                            (map (fn [o c] (/ (- c o) o)) opening current)))
                        multi-book-lines)
          
          ;; Check if movements are synchronized (same direction, similar magnitude)
          avg-movements (apply map (fn [& moves]
                                     (util/mean moves))
                              movements)
          
          threshold (config/get-config [:sharp :steam-threshold])
          
          steam-selections
          (for [i (range (count avg-movements))]
            (let [avg-move (nth avg-movements i)
                  all-same-direction? (every? #(= (pos? (nth % i)) (pos? avg-move))
                                             movements)
                  magnitude (Math/abs avg-move)]
              (when (and all-same-direction? (> magnitude threshold))
                {:selection-index i
                 :movement magnitude
                 :direction (if (= i 0) "home" (if (= i 2) "away" "draw"))
                 :num-books (count multi-book-lines)})))]
      
      (filter some? steam-selections))))

(defn create-steam-signal
  "Create steam move sharp signal"
  [match-id market steam-data]
  (let [confidence (util/clamp
                    (* 0.75 (/ (:movement steam-data) 0.05))
                    0.70
                    0.95)]
    {:signal-type :steam
     :match-id match-id
     :market market
     :direction (:direction steam-data)
     :confidence (util/round confidence 2)
     :evidence [(str "Synchronized movement across " (:num-books steam-data) " bookmakers")
                (str "Average movement: " (util/percentage (:movement steam-data)))]
     :timestamp (util/now)}))

;; ============================================================================
;; Contrarian Indicator
;; ============================================================================

(defn detect-contrarian
  "Detect contrarian betting opportunities
   When public heavily on one side but value suggests the other"
  [public-percentages ev-results]
  (for [i (range (count public-percentages))]
    (let [public-pct (nth public-percentages i)
          ev (get-in ev-results [i :ev] 0)
          
          ;; Contrarian: Public heavy on opposite, but we have +EV
          public-heavy-opposite? (< public-pct 0.35)  ;; Less than 35% public
          has-value? (> ev 0.05)]
      
      (when (and public-heavy-opposite? has-value?)
        {:selection-index i
         :public-percentage public-pct
         :ev ev
         :direction (if (= i 0) "home" (if (= i 2) "away" "draw"))}))))

(defn create-contrarian-signal
  "Create contrarian sharp signal"
  [match-id market contrarian-data]
  (let [confidence (util/clamp
                    (* 0.65 (+ 1.0 (:ev contrarian-data)))
                    0.65
                    0.90)]
    {:signal-type :contrarian
     :match-id match-id
     :market market
     :direction (:direction contrarian-data)
     :confidence (util/round confidence 2)
     :evidence [(str "Only " (util/percentage (:public-percentage contrarian-data)) " public support")
                (str "EV: " (util/format-ev (:ev contrarian-data)))]
     :timestamp (util/now)
     :public-percentage (:public-percentage contrarian-data)}))

;; ============================================================================
;; Betting Percentage Analysis
;; ============================================================================

(defn simulate-public-percentages
  "Simulate public betting percentages (mock data)"
  []
  ;; In real implementation, this would come from betting percentage data
  ;; For MVP, generate realistic distributions
  (let [home-pct (util/random-between 0.3 0.7)
        away-pct (util/random-between 0.2 (- 1.0 home-pct))
        draw-pct (- 1.0 home-pct away-pct)]
    [home-pct draw-pct away-pct]))

(defn calculate-money-vs-bets-divergence
  "Calculate divergence between bet count % and money %"
  [bet-percentages money-percentages]
  (mapv (fn [bet-pct money-pct]
          (- money-pct bet-pct))
        bet-percentages
        money-percentages))

;; ============================================================================
;; Comprehensive Sharp Analysis
;; ============================================================================

(defn analyze-match-for-sharp-money
  "Comprehensive sharp money analysis for a match"
  [match-data]
  (let [match-id (get-in match-data [:match :id])
        market :match-result
        line-history (:line-history match-data)
        public-pcts (simulate-public-percentages)
        
        ;; Detect different signal types
        rlm-detections (detect-rlm line-history public-pcts)
        ;; Steam would require multi-book data
        ;; Contrarian requires EV data
        
        ;; Create signals
        signals (concat
                 (map #(create-rlm-signal match-id market %) rlm-detections))]
    
    (when (seq signals)
      {:match-id match-id
       :signals signals
       :highest-confidence (apply max (map :confidence signals))})))

(defn scan-for-sharp-signals
  "Scan all matches for sharp money signals"
  [matches]
  (let [analyses (keep analyze-match-for-sharp-money matches)
        min-confidence (config/get-config [:sharp :min-confidence])
        filtered (filter #(>= (:highest-confidence %) min-confidence) analyses)]
    {:total-analyzed (count matches)
     :signals-detected (count filtered)
     :sharp-matches filtered}))

;; ============================================================================
;; Historical Pattern Matching
;; ============================================================================

(defn record-signal
  "Record sharp signal in history"
  [signal]
  (swap! sharp-signals conj signal)
  signal)

(defn get-historical-signals
  "Get historical sharp signals with optional filters"
  [& {:keys [signal-type min-confidence hours-back]}]
  (let [cutoff (when hours-back (util/hours-ago hours-back))]
    (->> @sharp-signals
         (filter (fn [sig]
                  (and (or (nil? signal-type) (= (:signal-type sig) signal-type))
                       (or (nil? min-confidence) (>= (:confidence sig) min-confidence))
                       (or (nil? cutoff) (> (.getTime (:timestamp sig)) (.getTime cutoff))))))
         vec)))

(defn calculate-signal-accuracy
  "Calculate historical accuracy of sharp signals (requires settled bets)"
  [signals settled-bets]
  ;; This would compare signal predictions to actual outcomes
  ;; For MVP, return placeholder
  {:total-signals (count signals)
   :accuracy 0.75  ;; Target: 75%+
   :note "Requires historical bet settlement data"})

;; ============================================================================
;; Public API
;; ============================================================================

(defn initialize!
  "Initialize sharp detector"
  []
  (reset! sharp-signals [])
  :initialized)

(defn detect-sharp-money
  "Main entry point: detect sharp money for a match"
  [match-data]
  (let [analysis (analyze-match-for-sharp-money match-data)]
    (when analysis
      ;; Record all signals
      (doseq [signal (:signals analysis)]
        (record-signal signal))
      analysis)))

(defn get-sharp-plays
  "Get current sharp plays above confidence threshold"
  [min-confidence]
  (get-historical-signals :min-confidence min-confidence :hours-back 48))
