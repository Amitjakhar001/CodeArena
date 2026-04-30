# Build context: repository root.
#   docker build -t auth-service -f auth-service/Dockerfile .
#
# We need the parent POM and the proto/ directory at build time, so building
# from auth-service/ alone is not enough.

FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /build

COPY pom.xml ./
COPY proto ./proto
COPY auth-service/pom.xml ./auth-service/

RUN mvn -B -pl auth-service -am dependency:go-offline

COPY auth-service/src ./auth-service/src
RUN mvn -B -pl auth-service -am package -DskipTests

FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

RUN addgroup -S app && adduser -S app -G app
USER app

COPY --from=builder /build/auth-service/target/auth-service-*.jar /app/app.jar

EXPOSE 8080 9090

ENTRYPOINT ["java", "-XX:+UseContainerSupport", "-jar", "/app/app.jar"]
