# Multi-stage build: dependencies cached separately from source
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

COPY pom.xml .
COPY services/auth-service/pom.xml services/auth-service/
RUN mvn -q -pl services/auth-service -am dependency:go-offline -B

COPY services/auth-service/src services/auth-service/src
RUN mvn -q -pl services/auth-service -am package -DskipTests -B

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=build /build/services/auth-service/target/*.jar app.jar

EXPOSE 8081
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "app.jar"]
