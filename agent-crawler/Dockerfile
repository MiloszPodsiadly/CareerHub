FROM gradle:8.8-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle --no-daemon -x test :agent-crawler:bootJar

FROM eclipse-temurin:21-jre-jammy
RUN groupadd -r app && useradd -r -g app -s /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /workspace/agent-crawler/build/libs/*.jar app.jar
RUN chown -R app:app /app
USER app
ENV JAVA_OPTS=""
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
