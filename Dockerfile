# Stage 1: Build the application
FROM maven:3.9.6-eclipse-temurin-21 AS build
WORKDIR /app
COPY pom.xml .
# Download dependencies (this step is cached if pom.xml doesn't change)
RUN mvn dependency:go-offline -B || true
COPY src ./src
# Build the application, skipping tests for faster deployment
RUN mvn clean package -DskipTests

# Stage 2: Run the application
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
# Copy the built jar file from the build stage
COPY --from=build /app/target/jobportal-0.0.1-SNAPSHOT.jar app.jar
# Expose the default port Render expects
EXPOSE 8080

# Run the application with dynamic PORT resolution, 0.0.0.0 interface binding, and 512MB RAM constraints
ENTRYPOINT ["sh", "-c", "java -XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0 -Xss512k -Dserver.port=${PORT:-8080} -Dserver.address=0.0.0.0 -jar app.jar"]

