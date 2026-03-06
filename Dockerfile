# Build Stage
FROM clojure:lein-2.9.10-alpine AS builder

WORKDIR /app

# Cache dependencies
COPY project.clj /app/
RUN lein deps

# Build the uberjar
COPY . /app/
RUN lein uberjar

# Production Stage
FROM eclipse-temurin:11-jre-alpine

WORKDIR /app

# Create and expose data directory for the EDN persistent ledger
RUN mkdir -p /app/data
VOLUME /app/data

# Default Production Environment Variables
ENV PORT=3000
ENV HOST="0.0.0.0"
ENV MOCK_MODE="false"
ENV ODDS_API_KEY="your-odds-api-key-here"
ENV API_KEY="your-secure-internal-auth-token"
ENV LOG_LEVEL="info"

# Copy the compiled uberjar from the builder stage
COPY --from=builder /app/target/*-standalone.jar /app/the-house-edge.jar

EXPOSE 3000

CMD ["java", "-jar", "the-house-edge.jar"]
