# syntax=docker/dockerfile:1

# ---- Build stage ----------------------------------------------------------
# Uses the official Gradle image rather than `./gradlew` - this repo has no committed
# Gradle wrapper, so pinning the build tool version here (instead of relying on
# whatever `gradle` happens to be on a dev machine) is what keeps the build reproducible.
FROM gradle:8.14-jdk21-alpine AS build
WORKDIR /workspace

# Copy only the build definition first so Docker can cache dependency resolution
# in its own layer, independent of source code changes below. `dependencies` only
# resolves the configuration graph (it doesn't need src/ to exist), so this is a
# safe, side-effect-free way to warm that cache; `|| true` keeps this purely a
# best-effort optimization - if it fails for any reason, the real build below
# re-resolves everything and surfaces the actual error there instead.
COPY build.gradle settings.gradle gradle.properties ./
RUN gradle --no-daemon dependencies || true

# Now bring in the real source and produce the executable jar. Tests are skipped
# here on purpose: this image build packages already-reviewed/CI-tested code, and
# this project's real test suite (Testcontainers-backed integration tests) needs
# Docker-in-Docker to run, which belongs in a CI pipeline, not an image build.
COPY src src
RUN gradle build -x test --no-daemon

# ---- Runtime stage ---------------------------------------------------------
# Separate, minimal JRE-only (no compiler/build tool) image - keeps the shipped
# image small and reduces attack surface versus reusing the JDK/Gradle build image.
FROM eclipse-temurin:21-jre-alpine
WORKDIR /app

# Run as a dedicated, unprivileged user - an internet-facing JVM should never run as root.
RUN addgroup -S spring && adduser -S spring -G spring
USER spring:spring

# Wildcarded on purpose: the Spring Boot Gradle plugin names the jar
# "<rootProject.name>-<version>.jar" (see build.gradle/settings.gradle), so this
# avoids hardcoding a version string that would go stale on every release bump.
COPY --from=build /workspace/build/libs/*.jar app.jar

EXPOSE 8080

# Actuator's health endpoint already reports DB/Redis reachability (see
# management.endpoint.health.probes.enabled in application.yml), so this checks
# real downstream dependencies, not just "the JVM process is still running".
HEALTHCHECK --interval=30s --timeout=3s --start-period=45s --retries=3 \
    CMD wget -q --spider http://localhost:8080/actuator/health || exit 1

# Exec form (no shell) so SIGTERM from `docker stop` reaches the JVM directly,
# letting Spring Boot's graceful-shutdown hook run instead of the process being
# killed after the shell fails to forward the signal. JVM flags (heap size, etc.)
# can be supplied at run time via the JAVA_TOOL_OPTIONS env var, which the JVM
# itself reads automatically - no shell/entrypoint-script interpolation needed.
ENTRYPOINT ["java", "-jar", "app.jar"]
