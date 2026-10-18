# AI Platform Backend Dockerfile
# Multi-stage build for optimized production image

# Build stage
FROM maven:3.9-eclipse-temurin-21-alpine AS builder

WORKDIR /build

# Copy pom.xml first for dependency caching
COPY pom.xml .
RUN mvn dependency:go-offline -B

# Copy source code and build
COPY src ./src
RUN mvn clean package -DskipTests -B

# Runtime stage: JNA's bundled Linux dispatcher targets glibc. Keep a named
# Ubuntu release instead of relying on an unverified musl compatibility layer.
FROM eclipse-temurin:21-jre-noble

WORKDIR /app

# Add non-root user for security
RUN groupadd --gid 1001 appgroup && \
    useradd --uid 1001 --gid appgroup --create-home --shell /usr/sbin/nologin appuser

# Create apps directory for Spring Boot
RUN mkdir -p /app/data/datasets

# Copy the built JAR from build stage
COPY --from=builder /build/target/*.jar app.jar

# Set ownership
RUN chown -R appuser:appgroup /app

USER appuser

# Keep the container contract aligned with application.yml and Compose.
ENV SERVER_PORT=8080 \
    MANAGEMENT_ENDPOINTS_WEB_BASE_PATH=/api/actuator \
    MANAGEMENT_ENDPOINTS_WEB_EXPOSURE_INCLUDE=health

EXPOSE 8080

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=3 \
    CMD wget --no-verbose --tries=1 --spider http://localhost:8080/api/actuator/health || exit 1

# JVM optimization for containers
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -XX:+ExitOnOutOfMemoryError"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
