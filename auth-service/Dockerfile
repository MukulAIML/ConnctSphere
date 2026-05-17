FROM maven:3.9.6-eclipse-temurin-17 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn clean package -Dmaven.test.skip=true

FROM eclipse-temurin:17-jre-jammy
RUN apt-get update && apt-get install -y redis-server
WORKDIR /app
COPY --from=build /app/target/*.jar app.jar
ENTRYPOINT service redis-server start && java -jar app.jar
