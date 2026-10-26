# syntax=docker/dockerfile:1

# ---------- Stage 1: build the jar ----------
FROM eclipse-temurin:21-jdk AS build
WORKDIR /workspace

COPY .mvn/ .mvn/
COPY mvnw pom.xml ./
RUN chmod +x mvnw

COPY src/ src/
# Cache mount keeps ~/.m2 between builds, so dependencies download once.
# Tests are skipped here: CI runs `mvn verify` (including Testcontainers) before building the image.
RUN --mount=type=cache,target=/root/.m2 ./mvnw -B -q package -DskipTests

# Split the fat jar into layers: dependencies change rarely, application code often.
RUN cp target/*.jar application.jar \
 && java -Djarmode=tools -jar application.jar extract --layers --destination extracted

# ---------- Stage 2: minimal runtime ----------
FROM eclipse-temurin:21-jre
WORKDIR /app

RUN groupadd --system app && useradd --system --gid app --no-create-home app

# Least-frequently-changing layers first, so rebuilds only replace the last layer.
COPY --from=build --chown=app:app /workspace/extracted/dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/spring-boot-loader/ ./
COPY --from=build --chown=app:app /workspace/extracted/snapshot-dependencies/ ./
COPY --from=build --chown=app:app /workspace/extracted/application/ ./

USER app
EXPOSE 8080

# Size the heap from the container's memory limit, and exit (so the orchestrator restarts) on OOM.
ENV JAVA_TOOL_OPTIONS="-XX:MaxRAMPercentage=75.0 -XX:+ExitOnOutOfMemoryError"

# Exec form: java runs as PID 1 and receives SIGTERM directly, so graceful shutdown works.
ENTRYPOINT ["java", "-jar", "application.jar"]