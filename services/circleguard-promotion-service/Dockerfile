# Build context: circle-guard-public/ (repo root — NOT this directory)
# docker build -t circleguard-promotion-service:$(git rev-parse --short HEAD) \
#              -f services/circleguard-promotion-service/Dockerfile .

# Stage 1: Build
FROM gradle:8-jdk21 AS builder
WORKDIR /workspace
COPY . .
RUN ./gradlew :services:circleguard-promotion-service:bootJar -x test --no-daemon

# Stage 2: Final image
FROM localhost:5000/circleguard-base:jdk21-alpine
COPY --from=builder --chown=appuser:appgroup /workspace/services/circleguard-promotion-service/build/libs/*.jar /app/app.jar
EXPOSE 8088
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
