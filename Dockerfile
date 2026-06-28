# ---- Stage 1: Build ----
# Use the official Maven image bundled with Amazon Corretto 21 to compile the project
FROM maven:3.9-amazoncorretto-21 AS builder

# Set the working directory inside the container for all subsequent commands
WORKDIR /app

# Copy only the pom.xml first so Maven can download dependencies as a separate
# cached layer — rebuilds triggered by source changes skip this expensive step
COPY pom.xml .

# Download all declared dependencies into the local Maven cache inside the image
RUN mvn dependency:go-offline -q

# Copy the full source tree now that dependencies are cached
COPY src ./src

# Package the application into a fat JAR; skip tests as they run in CI separately.
# Dependencies are already cached from the layer above so no network calls are needed.
RUN mvn package -DskipTests -q


# ---- Stage 2: Runtime ----
# Use a minimal Amazon Corretto 21 JRE image — discards the Maven toolchain and
# all build-time artifacts, keeping the final image as small as possible
FROM amazoncorretto:21

# Set the working directory where the JAR will live at runtime
WORKDIR /app

# Copy only the compiled JAR from the build stage into the runtime image
COPY --from=builder /app/target/rmn-insights-api-0.1.0-SNAPSHOT.jar app.jar

# Declare the port the Spring Boot application listens on
EXPOSE 8080

# Default environment variables for all external dependencies.
# Every value here should be overridden at runtime via -e or docker-compose.
ENV REDIS_URL=redis://localhost:6379 \
    DRUID_BROKER_URL=http://localhost:8082 \
    SNOWFLAKE_ACCOUNT="" \
    SNOWFLAKE_USER="" \
    SNOWFLAKE_PASSWORD="" \
    JWT_SECRET=changeme

# Start the application.
# -XX:MaxRAMPercentage=75.0  — use up to 75% of the container memory limit for the JVM heap
# -Djava.security.egd=...    — faster SecureRandom seeding on Linux, avoids startup delay
ENTRYPOINT ["java", \
    "-XX:MaxRAMPercentage=75.0", \
    "-Djava.security.egd=file:/dev/./urandom", \
    "-jar", "app.jar"]
