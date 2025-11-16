FROM gradle:8.8-jdk21 AS build
WORKDIR /workspace
COPY . .
RUN gradle --no-daemon -x test :backend:bootJar

FROM eclipse-temurin:21-jre
RUN groupadd -r app && useradd -r -g app -s /usr/sbin/nologin app
WORKDIR /app
COPY --from=build /workspace/backend/build/libs/*.jar app.jar
RUN chown -R app:app /app
USER app
ENV JAVA_OPTS=""
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
