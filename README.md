# The House Edge
### High-Frequency Value Betting Engine

This is not an AI. This is a deterministic, high-throughput expected value (EV) engine. It ingests live betting market data, extracts the bookmaker's overround to establish a "true" consensus probability, and flags isolated pricing inefficiencies against the broad market.

If you don't understand mathematical variance, the Kelly Criterion, or how market makers limit winning accounts, do not use this. It is a strict statistical analyzer, not a get-rich-quick scheme.

## Core Architecture

The system is pure Clojure and ClojureScript, designed entirely around functional paradigms, immutable data structures, and non-blocking concurrency on the JVM.

### Data Pipeline & Concurrency Model
The engine pulls high-volume JSON streams from The Odds API. Instead of blocking the main thread during heavy I/O requests (e.g., weekends with 60+ soccer matches across UEFA, EPL, La Liga, and Serie A):

1. **Thread Pool Ingestion:** We use `pmap` across active sports leagues to concurrently parallelize the external HTTP I/O requests.
2. **`core.async` Streaming:** Parsed odds are pushed onto a `core.async` channel backed by an `async/sliding-buffer`. The sliding buffer explicitly drops the oldest data on backpressure to aggressively prevent producer deadlocks when 1000+ match prices are ingested simultaneously.
3. **Atomic State Caching:** The complex cross-bookmaker EV computation is mathematically expensive. A background worker periodically calculates the current EV state graph and `reset!`s it into a global `atom`. Live API requests to the Ring/Jetty HTTP server simply dereference (`@`) this atom, guaranteeing constant $O(1)$, ultra-low latency responses independent of the backend computation times.

### The Mathematics

**1. Consensus True Probability**
Bookmakers bake an "overround" (vig/margin/juice) into their odds so the implied probabilities inherently sum to > 100%. To find the *actual* probability $P$ of an outcome:
- We aggregate the odds across *every available bookmaker* for a given match.
- We average the market odds to find the strict consensus line.
- We compute the inverse ($1 / \text{odds}$) and normalize the fractional sums exactly to 1.0.

**2. Expected Value (+EV)**
Once the true, margin-free consensus probability $P$ is established, we scan individual bookmakers for outlying prices.
$$EV = (P \times \text{Bookmaker Odds}) - 1$$
Only selections yielding an $EV > 0.02$ (+2% long-term edge) are routed to the frontend.

**3. Fractional Kelly Staking**
Bankroll sizing utilizes a fractional Kelly Criterion to mathematically optimize compound capital growth while strictly managing drawdown variance:
$$f^* = \frac{p - q}{b}$$
Where $b$ is the proportion of the bet gained with a win, $p$ is the probability of winning, and $q$ is the probability of losing. The final stake is clamped to a static maximum constraint (e.g., 5% of total bankroll) and scaled down by 0.25 (Quarter Kelly) to absorb inevitable statistical variance limits.

## Frontend Client

The frontend is a ClojureScript Single Page Application (SPA).
- **Architecture:** Standard `re-frame` event loop pattern. UI DOM abstractions (Reagent/React) are strictly passive and reactively re-render to state subcriptions from the `app-db`.
- **Async Polling:** The main dashboard dispatches a standard `http-xhrio` effect every 60 seconds to sync the UI state with the backend `atom`.
- **Cryptographic Gate:** The platform is locked behind a strict "Vault" UI. It evaluates a user-provided `X-API-Key` injected into the network interceptor queue before any remote backend communication is authorized.

## Building and Running

**Prerequisites:**
- Java 11+
- Leiningen (Clojure build tool)

**Environment Setup:**
Set your runtime configuration variables (injected via `.env` or bash profile):
```bash
export ODDS_API_KEY="your_odds_api_token"
export API_KEY="your_frontend_auth_token"
```

**Development Runtime:**
Start the backend Ring server on `:3000`:
```bash
lein run
```

Compile the ClojureScript SPA to `/resources/public/js/compiled`:
```bash
lein run -m cljs.main --build app
```

## Deployment Architecture

The codebase ships with a highly-optimized, multi-stage `Dockerfile`. 
- **Stage 1 (Builder):** Bootstraps `node.js` and `lein` purely to compile the `cljs` bundle and compile the Ring backend down into a single massive Uberjar. 
- **Stage 2 (Production):** Drops all heavy build dependencies and surgically packages the raw `.jar` into a minimalistic `eclipse-temurin:11-jre-alpine` runtime image to reduce the container footprint and limit the network attack surface.

It is structured specifically for bare-metal microVM deployment via Fly.io.
```bash
flyctl deploy
```
