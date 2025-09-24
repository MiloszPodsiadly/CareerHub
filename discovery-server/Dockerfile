FROM eclipse-temurin:21-jre-alpine

USER root
RUN apk add --no-cache curl

ENV PORT=8761
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:+HeapDumpOnOutOfMemoryError"
ENV SPRING_AUTOCONFIGURE_EXCLUDE=org.springframework.cloud.config.server.config.ConfigServerAutoConfiguration

RUN addgroup -S app && adduser -S app -G app
USER app

WORKDIR /app
COPY build/libs/*SNAPSHOT.jar app.jar

EXPOSE 8761
HEALTHCHECK --interval=20s --timeout=3s --start-period=20s --retries=5 \
  CMD curl -sf http://127.0.0.1:${PORT}/actuator/health | grep -q '"status":"UP"' || exit 1

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
