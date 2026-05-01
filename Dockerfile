# ── Stage 1: Build ───────────────────────────────────────
FROM gradle:8.7-jdk17 AS builder

WORKDIR /app

# Copy Gradle files first for dependency caching
COPY build.gradle.kts settings.gradle.kts ./
COPY gradle ./gradle

# Download dependencies (cached layer)
RUN gradle dependencies --no-daemon || true

# Copy source code and build
COPY src ./src
RUN gradle bootJar --no-daemon

# ── Stage 2: Runtime ─────────────────────────────────────
FROM eclipse-temurin:17-jre-alpine

WORKDIR /app

# Create non-root user
RUN addgroup -S adopti && adduser -S adopti -G adopti

# Copy the built jar
COPY --from=builder /app/build/libs/*.jar app.jar

# Switch to non-root user
USER adopti

EXPOSE 8083

HEALTHCHECK --interval=30s --timeout=5s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8083/health || exit 1

ENTRYPOINT ["java", "-jar", "app.jar"]
