HackerX Translate is an AI-powered localization service designed specifically for technical content. It enables developers to quickly make their learning platforms and applications multilingual while preserving code integrity, placeholders, and structural formatting.
Key Features
Smart Translation Engine

Preserves code blocks, variables, and placeholders during translation
Maintains JSON/NDJSON structure and formatting
Supports both OpenAI ChatGPT and Google Gemini AI models

Scale & Efficiency

Single file or bulk upload processing
Asynchronous processing for large datasets
NDJSON optimization with intelligent batching
Zip file output for multiple translations

Production Ready

Docker containerization for easy deployment
Long timeout handling for complex translations
CORS preconfiguration
Cloud platform compatibility

Getting Started
Prerequisites

Java 17+
Maven 3.6+
OpenAI API key and/or Google Gemini API key

Installation Options
Option A: Docker Deployment
bash# Build the container
docker build -t hackerx-translate .

# Run with API keys
docker run -p 8080:8080 \
  -e OPENAI_API_KEY=your_openai_key \
  -e GEMINI_API_KEY=your_gemini_key \
  hackerx-translate

# Access the service
curl http://localhost:8080
Option B: Local Development
bash# Set environment variables
export OPENAI_API_KEY=your_openai_key
export GEMINI_API_KEY=your_gemini_key
export GOOGLE_APPLICATION_CREDENTIALS=path/to/credentials.json

# Build and run
mvn clean package
mvn spring-boot:run
API Reference
Single File Translation
Endpoint: POST /api/translate/single
Parameters:

file: Upload file (JSON/NDJSON)
language: Target language code (es, fr, de, etc.)
service: AI service (ChatGPT or Gemini)
fileType: File format (json or ndjson)

Example:
bashcurl -X POST http://localhost:8080/api/translate/single \
  -F "file=@content.json" \
  -F "language=es" \
  -F "service=ChatGPT" \
  -F "fileType=json"
App Localization
Endpoint: POST /api/translate/app-json
Optimized for UI key-value translation pairs.
Parameters:

file: JSON file with key-value pairs
language: Target language code
service: AI service provider

Bulk Translation
Endpoint: POST /api/translate/multiple
Parameters:

files[]: Multiple file uploads
language: Target language code
service: AI service provider
fileType: File format specification

Returns: Zip file containing all translated files
Supported Languages
Common language codes include:

es - Spanish
fr - French
de - German
pt - Portuguese
it - Italian
ja - Japanese
ko - Korean
zh - Chinese

Production Deployment
Cloud Platforms
The service is designed for cloud deployment on:

Google Cloud Run
AWS ECS/Fargate
Azure Container Instances
Kubernetes clusters

Security Best Practices

Store API keys as environment variables or secrets
Never commit API keys to version control
Configure appropriate CORS origins for your domains
Implement rate limiting and authentication as needed

Configuration
yaml# Example docker-compose.yml
version: '3.8'
services:
  hackerx-translate:
    build: .
    ports:
      - "8080:8080"
    environment:
      - OPENAI_API_KEY=${OPENAI_API_KEY}
      - GEMINI_API_KEY=${GEMINI_API_KEY}
    deploy:
      resources:
        limits:
          memory: 2G
          cpus: '1.0'
Use Cases
Learning Platforms

Course content localization
Exercise and quiz translations
Documentation and help text

Mobile/Web Applications

UI string translation
Error message localization
Help documentation

Content Management

Blog post translation
Product descriptions
User-generated content

Technical Architecture
The service is built on Spring Boot and provides:

RESTful API endpoints
Asynchronous processing queues
File upload handling with validation
AI service integration layer
Error handling and logging

For advanced configuration and customization, refer to the application properties and extend the service as needed for your specific use case.RetryClaude can make mistakes. Please double-check responses.
