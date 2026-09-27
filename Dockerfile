# --- Build stage -----------------------------------------------------------
FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /app

# Cache dependencies separately from source so `docker compose build` is fast on rebuilds.
COPY pom.xml .
RUN mvn -q -B dependency:go-offline

COPY src ./src
RUN mvn -q -B package -DskipTests

# --- Runtime stage -----------------------------------------------------------
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# curl is needed for the container healthcheck (docker-compose.yml); not present in this base image.
RUN apk add --no-cache curl

COPY --from=build /app/target/oauth.jar app.jar

# 3001 = API, 9464 = actuator (health + /actuator/prometheus)
EXPOSE 3001 9464
ENTRYPOINT ["java", "-jar", "app.jar"]
