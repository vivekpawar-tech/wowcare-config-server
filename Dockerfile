# syntax=docker/dockerfile:1
# Multi-stage Dockerfile — Maven build + test + slim runtime.
# (Spring Cloud Config Server. CI: wowcare-ci shared library.)

# Stage 1: Build
FROM public.ecr.aws/docker/library/maven:3.9-eclipse-temurin-21-alpine AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline -B -q
COPY src ./src
RUN mvn clean package -DskipTests -B

# Test stage: run unit tests + export JUnit reports (non-gating; UNSTABLE on failure).
FROM builder AS test
RUN mvn test -B || true
RUN mkdir -p /out && for d in $(find . -type d -name surefire-reports); do mkdir -p "/out/$d" && cp "$d"/*.xml "/out/$d/" 2>/dev/null || true; done; true

# Testout stage: export ONLY the JUnit XML (`--target testout --output`).
FROM scratch AS testout
COPY --from=test /out/ /

# Stage 2: Runtime
FROM public.ecr.aws/docker/library/eclipse-temurin:21-jre-alpine

RUN apk add --no-cache git && \
  addgroup -S configserver && adduser -S configserver -G configserver

WORKDIR /app
COPY --from=builder /app/target/*.jar app.jar
RUN chown -R configserver:configserver /app

USER configserver
EXPOSE 8888

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8888/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
