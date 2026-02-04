FROM clojure:lein-2.9.10-alpine

WORKDIR /app

# Copy project files
COPY project.clj /app/
RUN lein deps

# Copy source code
COPY . /app/

# Build uberjar
RUN lein uberjar

# Expose port
EXPOSE 3000

# Run the application
CMD ["java", "-jar", "target/uberjar/the-house-edge-standalone.jar"]
