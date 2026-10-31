# ============================================================
# FoodBridge Java Backend - Production Multi-Stage Dockerfile
# Compatible with Render, Railway, and standard Docker hosts
# ============================================================

# Stage 1: Build the executable JAR with Maven
FROM maven:3.9.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app

# Cache dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source code and build fat JAR
COPY src ./src
COPY web ./web
RUN mvn clean package -DskipTests

# Stage 2: Lightweight JRE runtime
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Copy built application and static assets
COPY --from=builder /app/target/foodbridge-server.jar /app/foodbridge-server.jar
COPY --from=builder /app/web /app/web

# Default environment variables
ENV PORT=8080
EXPOSE 8080

# Run the backend
CMD ["java", "-jar", "/app/foodbridge-server.jar"]
