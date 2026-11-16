FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /workspace

# Cache dependencies before copying source. The service is a standalone Maven
# project (its parent is Spring Boot), so the monorepo root is not required.
COPY services/order-service/pom.xml services/order-service/pom.xml
RUN mvn -B -f services/order-service/pom.xml dependency:go-offline -DskipTests

COPY services/order-service/src services/order-service/src
RUN mvn -B -f services/order-service/pom.xml package -DskipTests

FROM eclipse-temurin:21-jre
WORKDIR /app
COPY --from=build /workspace/services/order-service/target/order-service.jar /app/order-service.jar
EXPOSE 8080
ENTRYPOINT ["java", "-XX:MaxRAMPercentage=75", "-jar", "/app/order-service.jar"]
