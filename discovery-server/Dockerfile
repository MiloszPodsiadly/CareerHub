FROM gradle:8.8-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle --no-daemon -x test :discovery-server:bootJar

FROM eclipse-temurin:21-jre-alpine
RUN apk add --no-cache curl \
    && addgroup -S app \
    && adduser -S app -G app
WORKDIR /app
COPY --from=build /workspace/discovery-server/build/libs/*.jar /app/app.jar
RUN chown -R app:app /app
USER app
ENV PORT=8761
ENV SPRING_AUTOCONFIGURE_EXCLUDE=org.springframework.cloud.config.server.config.ConfigServerAutoConfiguration
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75 -XX:+HeapDumpOnOutOfMemoryError"
EXPOSE 8761
HEALTHCHECK --interval=20s --timeout=3s --start-period=20s --retries=5 \
  CMD curl -fsS "http://127.0.0.1:${PORT}/actuator/health" >/dev/null || exit 1
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
