# ==============================================================================
# Propio Multi-Stage Dockerfile (Production Enterprise Grade)
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build Stage (Official Maven with Eclipse Temurin JDK 17)
# ------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17 AS build
WORKDIR /build

# Cache maven dependencies
COPY pom.xml .
RUN mvn -B dependency:go-offline

# Copy source code and package application artifact
COPY src ./src
RUN mvn -B -DskipTests package

# ------------------------------------------------------------------------------
# Stage 2: Production JRE 17 Runtime Stage
# ------------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy AS runtime

LABEL maintainer="Propio Engineering <engineering@propio.com>"
LABEL description="Propio - Enterprise Property Management SaaS Platform"

# Install curl for container health check and provision unprivileged system user
RUN apt-get update \
    && apt-get install -y --no-install-recommends curl \
    && rm -rf /var/lib/apt/lists/* \
    && groupadd -r -g 1001 spring \
    && useradd -r -u 1001 -g spring spring

WORKDIR /app

# Prepare upload directory and establish non-root ownership
RUN mkdir -p /app/uploads && chown -R spring:spring /app

# Copy executable application jar from build stage
COPY --from=build --chown=spring:spring /build/target/propio-*.jar /app/app.jar

# Enforce non-root execution
USER spring:spring

# Expose HTTP port
EXPOSE 8080

# Production environment variables & optimized JVM flags for 512 MB container
ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=8080 \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=70.0 -XX:+UseSerialGC -Xss512k -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

# Container healthcheck on Actuator endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:${PORT:-8080}/actuator/health || exit 1

# Launch application bound to dynamic host PORT
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -Dserver.port=${PORT:-8080} -jar /app/app.jar"]
