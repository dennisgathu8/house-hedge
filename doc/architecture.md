# The House Edge - Architecture Documentation

## System Overview

The House Edge is a professional betting intelligence platform built on Clojure's functional programming principles. The system treats betting as a data science problem, using immutable data structures, pure functions, and mathematical rigor.

---

## Core Philosophy

### 1. Immutability as Truth
Every bet is an immutable fact stored in a persistent vector. This enables:
- Time-travel queries on entire betting history
- Fork analysis (test alternative strategies on same data)
- Complete audit trail with no creative accounting

### 2. Expected Value as North Star
All recommendations based on quantified edge:
```
EV = (true-probability × odds) - 1
```

### 3. Professional Staking
Three strategies implemented:
- **Flat betting:** Fixed 1-2% per wager
- **Kelly Criterion:** Optimal growth stake sizing
- **Confidence-weighted:** 1-5 unit scale based on edge quality

---

## Agent Architecture

### Agent Coordination Pattern

```
┌─────────────────────────────────────────────────────────┐
│                    Core System                          │
│                (the-house-edge.core)                    │
└─────────────────────────────────────────────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ Agent Alpha  │  │ Agent Beta   │  │ Agent Gamma  │
│ Odds Engine  │  │ Sharp Detect │  │ Bankroll     │
└──────────────┘  └──────────────┘  └──────────────┘
        │                  │                  │
        └──────────────────┼──────────────────┘
                           │
        ┌──────────────────┼──────────────────┐
        │                  │                  │
        ▼                  ▼                  ▼
┌──────────────┐  ┌──────────────┐  ┌──────────────┐
│ Agent Delta  │  │ Agent Epsilon│  │ Agent Zeta   │
│ Analysis     │  │ Slips        │  │ Performance  │
└──────────────┘  └──────────────┘  └──────────────┘
```

### Data Flow

```
Match Fixtures
    │
    ├─→ Agent Alpha (Odds) ─→ EV Calculation
    │                              │
    ├─→ Agent Beta (Sharp) ─→ Sharp Signals
    │                              │
    ├─→ Agent Delta (Analysis) ─→ True Probability
    │                              │
    └─────────────┬────────────────┘
                  │
                  ▼
         Agent Epsilon (Slips)
         Investment-Grade Recommendations
                  │
                  ▼
         Agent Gamma (Bankroll)
         Staking Decision + Ledger
                  │
                  ▼
         Agent Zeta (Performance)
         ROI/Variance Tracking
```

---

## Protocol Definitions

**File:** `src/cljc/the_house_edge/protocol.cljc`

All inter-agent communication uses EDN schemas defined in the protocol namespace:

### Core Schemas

```clojure
;; Match fixture
{:id uuid
 :home-team string
 :away-team string
 :league string
 :kickoff inst}

;; Odds data
{:bookmaker string
 :match-id uuid
 :market keyword
 :prices [decimal]
 :timestamp inst}

;; Bet record (immutable)
{:id uuid
 :match-id uuid
 :market keyword
 :selection string
 :odds decimal
 :stake decimal
 :strategy keyword
 :ev decimal
 :timestamp inst
 :result keyword}
```

---

## State Management

### Immutable Stores

All state stored in atoms containing persistent vectors:

```clojure
;; Odds history (Agent Alpha)
(defonce odds-store (atom []))

;; Sharp signals (Agent Beta)
(defonce sharp-signals (atom []))

;; Bet ledger (Agent Gamma)
(defonce bet-ledger (atom []))
```

### Why Atoms + Persistent Vectors?

1. **Atomic updates** - Thread-safe without locks
2. **Structural sharing** - Efficient memory usage
3. **Time-travel** - Query any historical state
4. **Immutability** - No accidental mutations

---

## Agent Deep Dive

### Agent Alpha: Odds Engine

**Responsibilities:**
- Ingest odds from multiple bookmakers
- Calculate expected value
- Detect arbitrage opportunities
- Track historical line movement

**Key Algorithm: EV Calculation**

```clojure
(defn calculate-ev [true-prob odds]
  (- (* true-prob odds) 1.0))

;; Example:
;; True probability: 0.50 (50%)
;; Bookmaker odds: 2.20
;; EV = (0.50 × 2.20) - 1 = 0.10 (10% edge)
```

**Key Algorithm: Kelly Criterion**

```clojure
(defn kelly-criterion [bankroll edge odds fraction]
  (let [kelly-stake (* bankroll (/ edge (dec odds)))
        fractional-kelly (* kelly-stake fraction)]
    (max 0 fractional-kelly)))

;; Example:
;; Bankroll: $1000
;; Edge: 0.10 (10%)
;; Odds: 2.20
;; Fraction: 0.25 (quarter Kelly)
;; Stake = $1000 × (0.10 / 1.20) × 0.25 = $20.83
```

---

### Agent Beta: Sharp Detector

**Responsibilities:**
- Detect reverse line movement (RLM)
- Identify steam moves
- Generate contrarian signals
- Score confidence (0.0-1.0)

**Key Algorithm: RLM Detection**

```clojure
(defn detect-rlm [line-history public-percentages]
  ;; RLM occurs when:
  ;; 1. Public bets heavily on selection A (>55%)
  ;; 2. But line moves toward selection B (odds shorten)
  ;; This indicates sharp money on B
  )
```

**Signal Types:**

1. **RLM (Reverse Line Movement)**
   - Public: 65% on Team A
   - Line: Moves toward Team B
   - Interpretation: Sharp money on Team B

2. **Steam Move**
   - Synchronized line movement across 3+ bookmakers
   - Indicates coordinated sharp action

3. **Contrarian**
   - Public: <35% on selection
   - But: +EV > 5%
   - Interpretation: Public is wrong

---

### Agent Gamma: Bankroll Guardian

**Responsibilities:**
- Maintain immutable bet ledger
- Implement staking strategies
- Track bankroll history
- Enable fork simulation

**Key Algorithm: Fork Simulation**

```clojure
(defn simulate-alternative-strategy [alternative-strategy]
  (reduce
   (fn [state bet]
     (let [bankroll (:bankroll state)
           new-stake (calculate-stake alternative-strategy bankroll ...)
           profit (apply-result bet new-stake)
           new-bankroll (+ bankroll profit)]
       {:bankroll new-bankroll
        :bets (conj (:bets state) (assoc bet :stake new-stake))}))
   {:bankroll initial-bankroll :bets []}
   @bet-ledger))
```

**Staking Strategies:**

| Strategy | Formula | Risk Level |
|----------|---------|------------|
| Flat | `stake = bankroll × 0.02` | Low |
| Kelly | `stake = bankroll × (edge / (odds - 1)) × fraction` | Medium |
| Confidence | `stake = bankroll × 0.01 × units(1-5)` | Variable |

---

### Agent Delta: Match Analyst

**Responsibilities:**
- Integrate xG (expected goals) data
- Calculate team form with exponential decay
- Estimate true match probabilities
- Identify tactical edges

**Key Algorithm: Poisson Probability**

```clojure
(defn poisson-probability [lambda k]
  (/ (* (Math/pow lambda k) (Math/exp (- lambda)))
     (reduce * (range 1 (inc k)))))

;; Used to estimate match result probabilities
;; from expected goals
```

**Form Calculation:**

```clojure
(defn calculate-form-score [recent-matches]
  (let [decay 0.9  ;; Recent matches weighted more
        weighted-results
        (map-indexed
         (fn [idx match]
           (let [weight (Math/pow decay idx)
                 points (case (:result match)
                         :win 3.0
                         :draw 1.0
                         :loss 0.0)]
             (* weight points)))
         recent-matches)]
    (/ (reduce + weighted-results) max-possible)))
```

---

### Agent Epsilon: Slip Generator

**Responsibilities:**
- Transform analysis into actionable recommendations
- Generate investment-grade output format
- Integrate sharp signals
- Disclose risk factors

**Output Format:**

```clojure
{:recommendation-id uuid
 :match {:home "Arsenal" :away "Chelsea" :kickoff inst}
 :market :asian-handicap
 :selection "Arsenal -0.5"
 :odds 2.05
 :ev 0.087                    ;; 8.7% edge
 :kelly-stake 42.50           ;; Recommended stake
 :confidence 0.78             ;; 78% confidence
 :rationale "Sharp money on Arsenal despite 60% public on Chelsea. xG suggests Arsenal undervalued."
 :risk-factors ["Key midfielder suspended"]
 :sharp-signals [{:type :rlm :confidence 0.82}]}
```

---

### Agent Zeta: Performance Oracle

**Responsibilities:**
- Calculate ROI, yield, Sharpe ratio
- Track closing line value (CLV)
- Analyze variance (results vs. expectations)
- Enable time-travel queries

**Key Algorithm: Variance Analysis**

```clojure
(defn variance-analysis [ledger]
  (let [expected (reduce + (map #(* (:stake %) (:ev %)) ledger))
        actual (reduce + (map :profit ledger))
        delta (- actual expected)
        std-dev (standard-deviation (map :profit ledger))
        std-devs-away (/ delta std-dev)]
    {:expected-profit expected
     :actual-profit actual
     :std-devs-away std-devs-away
     :within-expectations? (<= (Math/abs std-devs-away) 2.0)}))
```

**Time-Travel Queries:**

```clojure
;; "ROI on away underdogs in rain?"
(time-travel-query @ledger
  #(and (= (:selection %) :away)
        (< (:odds %) 3.0)
        (= (:weather %) :rain)))
```

---

## Configuration System

**File:** `src/clj/the_house_edge/config.clj`

### Environment Variables

```bash
PORT=3000
LOG_LEVEL=info
DB_PATH=data/the-house-edge.edn
```

### Configuration Map

```clojure
{:server {:port 3000 :host "0.0.0.0"}
 :odds {:mock-mode? true
        :bookmakers [:pinnacle :betfair :bet365 :draftkings]}
 :bankroll {:default-strategy :kelly
            :kelly-fraction 0.25
            :initial-bankroll 1000.0}
 :slips {:min-ev 0.05
         :min-confidence 0.70
         :max-daily-slips 10}}
```

---

## Mock Data System

**File:** `src/cljc/the_house_edge/mock.cljc`

### Realistic Data Generation

1. **Matches:** 50 weekend fixtures across 5 leagues
2. **Odds:** Multiple bookmakers with realistic margins
3. **Line Movement:** Sharp moves, public pushes, steam
4. **xG Data:** Expected goals with variance
5. **Sharp Signals:** 30% of matches have signals

### Example Mock Match

```clojure
{:match {:id uuid
         :home-team "Arsenal"
         :away-team "Chelsea"
         :league "EPL"
         :kickoff #inst "2026-02-08T15:00:00"}
 :odds [{:bookmaker "Pinnacle"
         :prices [2.10 3.40 3.60]  ;; Home Draw Away
         :margin 0.02}]
 :true-probs {:home 0.45 :draw 0.28 :away 0.27}
 :sharp-signal {:type :rlm
                :direction "home"
                :confidence 0.82}}
```

---

## Security Invariants

### 1. No Dynamic Code Execution

```clojure
;; ❌ FORBIDDEN
(eval user-input)
(read-string untrusted-data)

;; ✅ ALLOWED
(s/validate schema data)  ;; Schema validation only
```

### 2. Immutable Audit Trail

```clojure
;; Every bet is a fact
(defn record-bet [bet]
  (swap! bet-ledger conj bet))  ;; Append-only

;; No mutations allowed
;; ❌ (assoc-in @bet-ledger [0 :result] :won)
```

### 3. Input Validation

```clojure
(defn validate-odds [odds]
  (and (number? odds) (> odds 1.0)))

(defn validate-stake [stake bankroll]
  (and (pos? stake) (<= stake (* bankroll 0.05))))
```

---

## Performance Optimizations

### 1. Lazy Evaluation

```clojure
;; Only analyze matches with +EV
(when (> ev 0.05)
  (deep-analysis match))
```

### 2. Memoization

```clojure
(def memoized-xg-calc
  (memoize
   (fn [team-id]
     (expensive-xg-calculation team-id))))
```

### 3. Parallel Processing

```clojure
;; Process weekend slate in parallel
(pmap analyze-match-ev fixtures)
```

---

## Testing Strategy

### Unit Tests

```clojure
(deftest test-ev-calculation
  (is (= 0.1 (calculate-ev 0.5 2.2))))

(deftest test-kelly-criterion
  (is (= 20.83 (kelly-criterion 1000 0.1 2.2 0.25))))
```

### Integration Tests

```clojure
(deftest test-full-pipeline
  (let [fixtures (generate-weekend-slate)
        recs (get-recommendations)]
    (is (> (:recommended-count recs) 0))
    (is (every? #(>= (:ev %) 0.05) (:slips recs)))))
```

### Performance Benchmarks

```clojure
(deftest test-performance
  (time
   (dotimes [_ 1000]
     (calculate-ev 0.5 2.2)))
  ;; Should complete in <5ms
  )
```

---

## Deployment Architecture

### Fly.io Configuration

```toml
[http_service]
  auto_stop_machines = true
  auto_start_machines = true
  min_machines_running = 0
```

**Benefits:**
- Auto-stop when idle (free tier optimization)
- Auto-start on request
- Zero cost when not in use

### Docker Container

```dockerfile
FROM clojure:lein-2.9.10-alpine
WORKDIR /app
COPY . /app/
RUN lein uberjar
CMD ["java", "-jar", "target/uberjar/the-house-edge-standalone.jar"]
```

---

## Future Architecture Considerations

### 1. Real API Integration

```clojure
(defn fetch-pinnacle-odds [match-id]
  (-> (http/get (str pinnacle-api-url "/odds/" match-id)
                {:headers {"X-API-Key" api-key}})
      :body
      json/parse-string))
```

### 2. Database Persistence

```clojure
;; Replace atoms with Datomic
(d/transact conn [{:db/id (d/tempid :db.part/user)
                   :bet/match-id match-id
                   :bet/odds odds
                   :bet/stake stake}])
```

### 3. Machine Learning

```clojure
(defn train-probability-model [historical-matches]
  ;; Use Clojure ML libraries
  ;; Train on xG, form, odds movement
  )
```

---

## Conclusion

The House Edge demonstrates how functional programming principles create a robust, transparent, and auditable betting intelligence system. The immutable ledger, pure functions, and agent-based architecture provide a foundation for professional-grade analysis.

**Key Achievements:**
- ✅ 6 specialized agents working in concert
- ✅ Immutable audit trail with time-travel queries
- ✅ Mathematical rigor (EV, Kelly, Poisson)
- ✅ Sharp money detection
- ✅ Fork simulation for strategy comparison
- ✅ Investment-grade recommendation format
- ✅ Radical transparency in performance tracking

**The platform is production-ready for MVP demonstration.**
