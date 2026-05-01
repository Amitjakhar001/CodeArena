# Build context: repository root.
#   docker build -t execution-service -f execution-service/Dockerfile .
#
# Parent POM and proto/ directory are needed at build time, so build context
# must be the repo root.

FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

COPY pom.xml ./
COPY proto ./proto
COPY execution-service/pom.xml ./execution-service/

RUN mvn -B -pl execution-service -am dependency:go-offline

COPY execution-service/src ./execution-service/src
RUN mvn -B -pl execution-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=builder /build/execution-service/target/execution-service-*.jar /app/app.jar

EXPOSE 8080 9090

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/app.jar"]
