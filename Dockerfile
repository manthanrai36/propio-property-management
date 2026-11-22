# ==============================================================================
# Propio Multi-Stage Dockerfile (Production Enterprise Grade)
# ==============================================================================

# ------------------------------------------------------------------------------
# Stage 1: Build Stage
# ------------------------------------------------------------------------------
FROM maven:3.9-eclipse-temurin-17-alpine AS build
WORKDIR /build

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build production artifact
COPY src ./src
RUN mvn clean package -DskipTests -B

# ------------------------------------------------------------------------------
# Stage 2: Production Minimal JRE Runtime Stage
# ------------------------------------------------------------------------------
FROM eclipse-temurin:17-jre-jammy AS runtime

# Metadata
LABEL maintainer="Propio Engineering <engineering@propio.com>"
LABEL description="Propio - Enterprise Property Management SaaS Platform"

# Install curl for health check
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/*

# Create unprivileged system user and group
RUN groupadd -r -g 1001 spring && useradd -r -u 1001 -g spring spring

WORKDIR /app

# Create upload directory and grant permissions
RUN mkdir -p /app/uploads && chown -R spring:spring /app

# Copy executable jar from build stage
COPY --from=build /build/target/propio-*.jar /app/app.jar
RUN chown spring:spring /app/app.jar

# Run as non-root user for container security
USER spring:spring

# Expose default HTTP port
EXPOSE 8080

# Environment Defaults
ENV SPRING_PROFILES_ACTIVE=prod \
    PORT=8080 \
    JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError -Djava.security.egd=file:/dev/./urandom"

# Health check using Actuator endpoint
HEALTHCHECK --interval=30s --timeout=5s --start-period=60s --retries=3 \
  CMD curl -f http://localhost:${PORT}/actuator/health || exit 1

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
