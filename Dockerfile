FROM maven:3.9.9-eclipse-temurin-21 AS builder

WORKDIR /app

COPY pom.xml .

RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -DskipTests dependency:go-offline

COPY src ./src

RUN --mount=type=cache,target=/root/.m2 \
    mvn -q -DskipTests clean package

FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

ENV TZ=Asia/Kolkata
ENV JAVA_TOOL_OPTIONS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

RUN groupadd -r spring && useradd -r -u 1001 -g spring spring

COPY --from=builder --chown=spring:spring \
    /app/target/*.jar app.jar

USER spring

EXPOSE 8082

ENTRYPOINT ["java", "-jar", "app.jar"]