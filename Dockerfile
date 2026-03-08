# Single-stage Dockerfile — uses pre-built JAR from Maven
FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache git && \
  addgroup -S configserver && adduser -S configserver -G configserver

WORKDIR /app
COPY target/*.jar app.jar
RUN chown -R configserver:configserver /app

USER configserver
EXPOSE 8888

HEALTHCHECK --interval=30s --timeout=10s --start-period=60s --retries=5 \
  CMD wget --no-verbose --tries=1 --spider http://localhost:8888/actuator/health || exit 1

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -XX:+UseG1GC -Djava.security.egd=file:/dev/./urandom"
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
