# Builds the app with the Vaadin production bundle (-Pproduction).
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
COPY src ./src
COPY frontend ./frontend
RUN mvn -B -Pproduction -DskipTests package

FROM eclipse-temurin:21-jre
RUN useradd -u 1001 -m appuser
WORKDIR /app
COPY --from=builder --chown=1001:1001 /app/target/questionnaire-*.jar app.jar
USER 1001
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75.0", "-jar", "app.jar"]
