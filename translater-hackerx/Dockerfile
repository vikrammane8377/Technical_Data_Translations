# Use the official OpenJDK 17 slim image as the base
FROM openjdk:17-jdk-slim

# Install Maven
RUN apt-get update && \
    apt-get install -y maven && \
    rm -rf /var/lib/apt/lists/*

# Create the output directory and make it writable
RUN mkdir -p /output && chmod 777 /output

# Set the working directory inside the container
WORKDIR /app

# Copy the Maven project files to the working directory
COPY . .

# Copy the Google Cloud credentials file into the container
COPY src/main/resources/hackerxtranslation-68c896a87f53.json /app/hackerxtranslation-68c896a87f53.json

# Set the environment variable for Google Cloud credentials
ENV GOOGLE_APPLICATION_CREDENTIALS=/app/hackerxtranslation-68c896a87f53.json

# Expose the port the application runs on
EXPOSE 8080

# Build the Maven project
RUN mvn clean package

# Copy the JAR file to the output directory
RUN cp /app/target/*.jar /output/

# Run the application
CMD ["sh", "-c", "java -jar /app/target/*.jar"]
