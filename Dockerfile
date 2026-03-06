# Build Stage
FROM clojure:lein-2.9.10-alpine AS builder

# Install Node.js for ClojureScript compilation
RUN apk add --no-cache nodejs npm

WORKDIR /app

# Cache dependencies
COPY project.clj /app/
RUN lein deps

# Build the uberjar
COPY . /app/
RUN lein uberjar && \
    find /app/target -name "*standalone.jar" -exec cp {} /app/the-house-edge.jar \;

# Production Stage
FROM eclipse-temurin:11-jre-alpine

WORKDIR /app

# Create data directory for the EDN persistent ledger
RUN mkdir -p /app/data

# Secrets are injected by Fly.io at runtime
ENV PORT=3000
ENV HOST="0.0.0.0"

# Copy the compiled uberjar from the builder stage
COPY --from=builder /app/the-house-edge.jar /app/the-house-edge.jar

EXPOSE 3000

# JVM tuning for small containers
CMD ["java", "-XX:+UseContainerSupport", "-XX:MaxRAMPercentage=75.0", "-jar", "the-house-edge.jar"]
