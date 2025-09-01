HackerX Translate — AI-powered localization for technical content

Make your learning platform or app multilingual—fast. Upload structured content files (JSON/NDJSON), choose a language, and get clean, production-ready translations that preserve code, placeholders, and layout.

- Translate at scale: single files or bulk uploads (zipped output)
- High fidelity: protects code, placeholders, and structure
- Best-in-class AI: choose OpenAI ChatGPT or Google Gemini
- Built for production: async processing, long timeouts, Docker-ready

What you can do
- Translate JSON content: returns a translated JSON with the same structure
- Translate NDJSON: optimized for large datasets with batching
- Translate app copy (key/value JSON): perfect for UI localization
- Download multiple translated files as a single zip

Quick start
Option A — Docker
- Build: docker build -t hackerx-translate .
- Run: docker run -p 8080:8080 -e OPENAI_API_KEY=... -e GEMINI_API_KEY=... hackerx-translate
- Open: http://localhost:8080

Option B — Local (Java 17 + Maven)
- Export env vars: OPENAI_API_KEY, GEMINI_API_KEY, GOOGLE_APPLICATION_CREDENTIALS
- Build: mvn clean package
- Run: mvn spring-boot:run

Core API endpoints
- POST /api/translate/single   (file, language, service, fileType=json|ndjson)
- POST /api/translate/app-json (file, language, service)
- POST /api/translate/multiple (files[], language, service, fileType) -> zip

Security and notes
- Provide API keys via environment variables; don’t commit secrets
- CORS preconfigured for known frontends; extend for your domains
- Supports long-running jobs; add gateway/rate limits in production

Deployment
- Ships as a single Dockerized Spring Boot service on port 8080
- Cloud ready (Cloud Run, ECS/Fargate, App Service, Kubernetes)
