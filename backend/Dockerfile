FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -B -DskipTests package

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /app/target/music-player-api-0.0.1-SNAPSHOT.jar app.jar
EXPOSE 8181
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
