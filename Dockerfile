# Stage 1 — build
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /app
COPY pom.xml .
RUN mvn dependency:go-offline
COPY src ./src
RUN mvn package -DskipTests

# Stage 2 — run (JRE only, Debian-based for ONNX Runtime glibc compatibility)
FROM eclipse-temurin:21-jre
WORKDIR /app

# Pre-download the ONNX model and tokenizer so startup is fast
RUN apt-get update && apt-get install -y --no-install-recommends curl && rm -rf /var/lib/apt/lists/* && \
    mkdir -p models/all-MiniLM-L6-v2 && \
    curl -L -o models/all-MiniLM-L6-v2/model.onnx \
      "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/onnx/model.onnx" && \
    curl -L -o models/all-MiniLM-L6-v2/tokenizer.json \
      "https://huggingface.co/sentence-transformers/all-MiniLM-L6-v2/resolve/main/tokenizer.json"

COPY --from=builder /app/target/search-api.jar app.jar

EXPOSE 8080
ENTRYPOINT ["java", "-jar", "app.jar"]