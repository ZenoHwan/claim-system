# syntax=docker/dockerfile:1
# One image definition for all three services: build with --build-arg MODULE=claims-service (etc).

FROM eclipse-temurin:21-jdk AS build
WORKDIR /src
COPY .mvn .mvn
COPY mvnw pom.xml ./
COPY claim-events claim-events
COPY claims-service claims-service
COPY reporting-service reporting-service
COPY notification-service notification-service
ARG MODULE
# Tests run in ./mvnw verify on the host (they need Docker for Testcontainers); the image build only packages.
RUN --mount=type=cache,target=/root/.m2,sharing=locked \
    ./mvnw -q -pl ${MODULE} -am -DskipTests package

FROM eclipse-temurin:21-jre
ARG MODULE
# Run as a non-root user; /data is created here so a mounted volume inherits its ownership.
RUN useradd --system --create-home app && mkdir -p /data/documents && chown -R app /data
WORKDIR /app
COPY --from=build /src/${MODULE}/target/${MODULE}-0.0.1-SNAPSHOT.jar app.jar
USER app
ENTRYPOINT ["java", "-jar", "app.jar"]
