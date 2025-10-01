FROM gradle:8.8-jdk21 AS builder
WORKDIR /workspace


COPY . .

RUN gradle --no-daemon -x test bootJar

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache wget curl \
 && addgroup -S app && adduser -S app -G app

USER app
WORKDIR /app

ENV PORT=8761
ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:+HeapDumpOnOutOfMemoryError"
ENV SPRING_AUTOCONFIGURE_EXCLUDE=org.springframework.cloud.config.server.config.ConfigServerAutoConfiguration

COPY --from=builder /workspace/build/libs/*.jar /app/app.jar

EXPOSE 8761

HEALTHCHECK --interval=20s --timeout=3s --start-period=20s --retries=5 \
  CMD wget -q --spider "http://127.0.0.1:${PORT}/actuator/health" || exit 1

ENTRYPOINT ["sh","-c","java $JAVA_OPTS -jar /app/app.jar"]
