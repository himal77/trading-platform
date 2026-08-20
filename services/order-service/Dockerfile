# Multi-stage build: dependencies cached separately from source
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY services/order-service/pom.xml services/order-service/
RUN mvn -q -pl services/order-service -am dependency:go-offline -B

COPY services/order-service/src services/order-service/src
RUN mvn -q -pl services/order-service -am package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /build/services/order-service/target/*.jar app.jar

EXPOSE 8084
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
