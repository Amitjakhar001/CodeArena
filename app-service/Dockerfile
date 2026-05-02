# Build context: repository root.
#   docker build -t app-service -f app-service/Dockerfile .
#
# Parent POM and proto/ directory are needed at build time, so build context
# must be the repo root.

FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

COPY pom.xml ./
COPY proto ./proto
COPY app-service/pom.xml ./app-service/

RUN mvn -B -pl app-service -am dependency:go-offline

COPY app-service/src ./app-service/src
RUN mvn -B -pl app-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=builder /build/app-service/target/app-service-*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/app.jar"]
