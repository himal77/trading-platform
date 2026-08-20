# Multi-stage build: dependencies cached separately from source
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY services/wallet-service/pom.xml services/wallet-service/
RUN mvn -q -pl services/wallet-service -am dependency:go-offline -B

COPY services/wallet-service/src services/wallet-service/src
RUN mvn -q -pl services/wallet-service -am package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /build/services/wallet-service/target/*.jar app.jar

EXPOSE 8082
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
