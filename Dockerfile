# Multi-stage Dockerfile for WowCare Config Server
# Stage 1: Build stage
FROM maven:3.9.6-eclipse-temurin-21-alpine AS builder

WORKDIR /app

# Copy project files
COPY pom.xml .
COPY src ./src

# Build the application (dependencies will be downloaded during build)
RUN mvn clean package -DskipTests -B

# Stage 2: Runtime stage
FROM eclipse-temurin:21-jre-alpine

# Install git and openssh for cloning config repositories
RUN apk add --no-cache git openssh-client

# Create non-root user for security
RUN addgroup -S configserver && adduser -S configserver -G configserver

# Set working directory
WORKDIR /app

# Copy the built JAR from builder stage
COPY --from=builder /app/target/*.jar app.jar

# Copy entrypoint script
COPY docker-entrypoint.sh /usr/local/bin/
RUN chmod +x /usr/local/bin/docker-entrypoint.sh

# Create SSH directory with proper permissions
RUN mkdir -p /home/configserver/.ssh && \
    chown -R configserver:configserver /home/configserver/.ssh && \
    chmod 700 /home/configserver/.ssh

# Change ownership of app directory
RUN chown -R configserver:configserver /app

# Switch to non-root user
USER configserver

# Expose Config Server port
EXPOSE 8888

# Health check
HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8888/actuator/health || exit 1

# Set the entrypoint to use the script
ENTRYPOINT ["/usr/local/bin/docker-entrypoint.sh"]
