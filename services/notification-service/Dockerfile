# Multi-stage build: dependencies cached separately from source
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY services/notification-service/pom.xml services/notification-service/
RUN mvn -q -pl services/notification-service -am dependency:go-offline -B

COPY services/notification-service/src services/notification-service/src
RUN mvn -q -pl services/notification-service -am package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /build/services/notification-service/target/*.jar app.jar

EXPOSE 8085
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
