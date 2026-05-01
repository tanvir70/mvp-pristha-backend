# Stage 1: Build the application
FROM eclipse-temurin:25-jdk AS builder
WORKDIR /app

# Copy the gradle wrapper and required files
COPY gradlew .
COPY gradle/ gradle/
COPY build.gradle settings.gradle ./
COPY src/ src/

# Ensure the gradlew script is executable and build the project
RUN chmod +x gradlew
RUN ./gradlew clean build -x test

# Stage 2: Create the runtime image
FROM eclipse-temurin:25-jre
WORKDIR /app

# Copy the executable jar from the build stage
COPY --from=builder /app/build/libs/*.jar app.jar

# Cloud platforms like Render provide the port via the PORT environment variable
ENV PORT=8080
EXPOSE $PORT

# Start the application, passing the PORT to Spring Boot
ENTRYPOINT ["sh", "-c", "java -Dserver.port=${PORT} -jar app.jar"]
