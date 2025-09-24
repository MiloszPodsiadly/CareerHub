FROM gradle:8.8-jdk21 AS build
WORKDIR /workspace
COPY . .

RUN gradle --no-daemon -x test :backend:bootJar

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/backend/build/libs/*.jar app.jar

ENV JAVA_OPTS=""
EXPOSE 8081
ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar /app/app.jar"]
