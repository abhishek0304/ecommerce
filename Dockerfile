# syntax=docker/dockerfile:1
FROM maven:3.9.9-eclipse-temurin-21 AS build
WORKDIR /workspace
COPY . .
ARG MODULE
RUN --mount=type=cache,target=/root/.m2,sharing=locked test -n "$MODULE" && mvn -B -f "$MODULE/pom.xml" -DskipTests package && cp "$MODULE"/target/*-SNAPSHOT.jar /app.jar
FROM eclipse-temurin:21-jre-jammy
RUN groupadd -g 10001 app && useradd -u 10001 -g app app && mkdir /logs && chown app:app /logs
WORKDIR /app
COPY --from=build --chown=app:app /app.jar app.jar
COPY ecommerce-config-repo /config-repo
USER 10001:10001
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=65.0"
ENTRYPOINT ["java","-jar","/app/app.jar"]

