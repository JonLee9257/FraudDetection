# Build
FROM maven:3.9-eclipse-temurin-25 AS build
WORKDIR /app
COPY pom.xml .
COPY src ./src
RUN mvn -q clean package -DskipTests \
    && cp /app/target/fraud-detection-engine-*-SNAPSHOT.jar /app/app.jar

# Run (default: FraudDetectionApplication; override entrypoint for Flink with PropertiesLauncher)
FROM eclipse-temurin:25-jre
WORKDIR /app
COPY --from=build /app/app.jar app.jar
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
