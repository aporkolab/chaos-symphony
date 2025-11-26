# Stage 1: Build the application using Maven
FROM maven:3.9-eclipse-temurin-21 AS builder

# Set the working directory
WORKDIR /app

# Copy the Maven wrapper and the parent POM file
# This helps leverage Docker's layer caching for dependencies
COPY .mvn/ .mvn
COPY mvnw pom.xml ./

# Copy the entire project source code
# In a monorepo, it's often easiest to copy everything to ensure all
# inter-module dependencies are available during the build.
COPY . .

# Build the target service ('order-api') and its dependencies ('-am')
# -B runs in batch mode
# -DskipTests skips running tests during the Docker build
RUN mvn -B package -pl order-api -am -DskipTests

# ---

# Stage 2: Create the final, lightweight image
FROM eclipse-temurin:21-jre-jammy

WORKDIR /app

# Download OpenTelemetry Java agent
ADD https://github.com/open-telemetry/opentelemetry-java-instrumentation/releases/download/v2.10.0/opentelemetry-javaagent.jar /app/opentelemetry-javaagent.jar

# Copy the executable JAR from the builder stage
COPY --from=builder /app/order-api/target/*.jar ./application.jar

# Expose the port that the application will run on
EXPOSE 8080

# Set the entrypoint to run the application with OTel agent
ENTRYPOINT ["java", "-javaagent:/app/opentelemetry-javaagent.jar", "-jar", "./application.jar"]
