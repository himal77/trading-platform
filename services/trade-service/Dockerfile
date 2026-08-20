# Multi-stage build: dependencies cached separately from source
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY services/trade-service/pom.xml services/trade-service/
RUN mvn -q -pl services/trade-service -am dependency:go-offline -B

COPY services/trade-service/src services/trade-service/src
RUN mvn -q -pl services/trade-service -am package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /build/services/trade-service/target/*.jar app.jar

EXPOSE 8083
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
