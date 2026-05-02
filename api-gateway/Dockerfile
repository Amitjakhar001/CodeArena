# Build context: repository root.
#   docker build -t api-gateway -f api-gateway/Dockerfile .
# Build context must be repo root because the parent POM lives there.

FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

COPY pom.xml ./
COPY api-gateway/pom.xml ./api-gateway/

RUN mvn -B -pl api-gateway -am dependency:go-offline

COPY api-gateway/src ./api-gateway/src
RUN mvn -B -pl api-gateway -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=builder /build/api-gateway/target/api-gateway-*.jar /app/app.jar

EXPOSE 8080

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/app.jar"]
