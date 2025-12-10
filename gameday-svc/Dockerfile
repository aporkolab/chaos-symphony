# Stage 1: Build the application using Maven
FROM maven:3.9-eclipse-temurin-21 AS builder

# Set the working directory
WORKDIR /app

# Copy the Maven wrapper and the parent POM file
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Copy the entire project source code
COPY . .

# Build the target service and its dependencies
RUN mvn -B package -pl gameday-svc -am -DskipTests

# ---

# Stage 2: Create the final, lightweight image
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Download OpenTelemetry Java agent
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.10.0/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

# Copy the executable JAR from the builder stage
COPY --from=builder /app/gameday-svc/target/*.jar ./application.jar

# Expose the port that the application will run on
EXPOSE 8080

# Set the entrypoint to run the application with OTel agent
ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "./application.jar"]
