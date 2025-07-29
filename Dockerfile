FROM gradle:8.14-jdk21 AS build

WORKDIR /app

# Copy gradle files first for better caching
COPY build.gradle.kts gradle.properties settings.gradle.kts ./
COPY gradle/ gradle/

# Download dependencies
RUN gradle dependencies --no-daemon

# Copy source code
COPY src/ src/

# Build the application
RUN gradle build --no-daemon -x test

# Runtime stage
FROM openjdk:21-jre-slim

WORKDIR /app

# Create app user
RUN groupadd -r app && useradd -r -g app app

# Create directories
RUN mkdir -p data/books && chown -R app:app data/

# Copy built jar
COPY --from=build /app/build/libs/*.jar app.jar

# Switch to app user
USER app

EXPOSE 8080

CMD ["java", "-jar", "app.jar"]