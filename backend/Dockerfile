# Multi-stage build: compile with the JDK, ship only the JRE.
#
# Built by the hosting provider (or manually on a permitted machine) from the
# repository root:
#   docker build -f backend/Dockerfile -t joblens-api backend
#
# Tests are NOT run here -- CI is responsible for that, and running them during
# an image build would require a database inside the builder.

FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build

# Copy the POM first so dependency resolution is cached separately from sources.
COPY pom.xml .
RUN mvn --batch-mode dependency:go-offline

COPY src ./src
RUN mvn --batch-mode -DskipTests package

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as a non-root user.
RUN addgroup -S joblens && adduser -S joblens -G joblens
USER joblens

COPY --from=build /build/target/*.jar app.jar

# The provider injects PORT; application.yml honours it.
EXPOSE 8080

ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/app.jar"]
