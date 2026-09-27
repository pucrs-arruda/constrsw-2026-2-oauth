FROM maven:3.9.11-eclipse-temurin-21-alpine AS build

WORKDIR /workspace
COPY pom.xml .
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src src
RUN mvn --batch-mode --no-transfer-progress package -DskipTests

FROM eclipse-temurin:21-jre-alpine

RUN apk add --no-cache curl \
    && addgroup -S spring \
    && adduser -S spring -G spring

USER spring:spring
WORKDIR /app
COPY --from=build --chown=spring:spring /workspace/target/oauth-*.jar app.jar

EXPOSE 3001 9464
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
