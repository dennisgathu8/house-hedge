# The House Edge

This is a sports betting odds analyzer. It looks for value bets (positive expected value / +EV) by comparing odds across multiple bookmakers.

## What it actually does

It fetches live odds from The Odds API for several soccer leagues (EPL, La Liga, Serie A, Champions League). It parses the data to find the "consensus" implied probability of an outcome by averaging the odds across every available bookmaker. 

If one specific bookmaker is offering odds that are significantly higher than the consensus average (yielding an EV of > 2%), it flags it as a value bet and calculates a recommended stake using a fractional Kelly Criterion.

It uses a background thread to poll the external API every 5 minutes and caches the results in memory. The frontend is a Single Page Application that reads this cache.

## What it DOES NOT do

- It does not guarantee you will make money. If you don't understand variance or how bookmakers limit winning accounts, you will probably lose money. 
- It does not have artificial intelligence or machine learning. It is basic arithmetic, probability theory, and core.async channels applied to JSON payloads.
- It does not automatically place bets for you. 

## Architecture

It's written entirely in Clojure and ClojureScript.

**Backend:**
- `core.async` `sliding-buffer` channels are used to concurrently ingest and process the odds without deadlocking the memory threads.
- An in-memory `atom` caches the latest computations so the API endpoint doesn't block and returns instantly.
- A standard Ring/Jetty HTTP server provides the API and serves the static assets.

**Frontend:**
- Built with Reagent and re-frame.
- Polls the backend every 60 seconds to refresh the dashboard.
- Includes a basic API key authorization gate ("The Vault").

## Running it

You need Java 11+ and Leiningen.

1. Get an API key from [The Odds API](https://the-odds-api.com/).
2. Set it in your environment: `export ODDS_API_KEY="your_key"`
3. If you want to use the frontend Vault, set your own security key: `export API_KEY="your_secret"`
4. Start the server:
   ```bash
   lein run
   ```
5. The server listens on `http://localhost:3000`.

To compile the ClojureScript frontend locally:
```bash
lein run -m cljs.main --build app
```

## Deployment

There's a `Dockerfile` included. It uses multi-stage builds to compile the uberjar and packages it into a minimal Alpine JRE image. It is currently configured for Fly.io deployments.

```bash
flyctl deploy
```

That's it.
