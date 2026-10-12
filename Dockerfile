# ============================================================
#  MoveInSync Vendor & Driver Settlement Engine
#  Multi-Stage Dockerfile
#
#  Stage 1 (builder): Compile with Maven + JDK 21 and build
#                     the executable fat JAR.
#  Stage 2 (runtime): Copy only the JAR into a minimal JRE
#                     image. Runs as a non-root system user.
#
#  Build:  docker build -t settlement-engine:latest .
#  Run:    docker run -p 8080:8080 settlement-engine:latest
# ============================================================

# ── Stage 1: Build ─────────────────────────────────────────
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

# Copy pom.xml first so Docker can cache the dependency layer.
# The heavy go-offline step is skipped on subsequent builds
# when only source code changes.
COPY pom.xml ./
RUN mvn dependency:go-offline -B -q || true

# Copy source and build the production JAR.
# Tests are skipped here — they are run separately in the CI test stage.
COPY src ./src
RUN mvn clean package -DskipTests -B -q

# ── Stage 2: Runtime ───────────────────────────────────────
# eclipse-temurin:21-jre-jammy is a minimal Temurin JRE image (~220 MB)
# with no build tools, reducing the attack surface significantly.
FROM eclipse-temurin:21-jre-jammy AS runtime
WORKDIR /app

# Create a dedicated non-root system user + group.
# The container never runs as root.
RUN addgroup --system settlement \
 && adduser  --system --ingroup settlement --no-create-home settlement

# Copy the compiled JAR from the builder stage.
COPY --from=builder /workspace/target/settlement-engine-*.jar app.jar

# Give ownership to our non-root user.
RUN chown settlement:settlement app.jar

USER settlement:settlement

EXPOSE 8080

# JVM tuning flags:
#   UseContainerSupport   — honours cgroup CPU/memory limits (default in Java 11+)
#   MaxRAMPercentage=75.0 — allocate 75% of container RAM to the heap
#   java.security.egd    — faster SecureRandom seeding in containers (avoids /dev/random blocking)
ENV JAVA_OPTS="-XX:+UseContainerSupport \
               -XX:MaxRAMPercentage=75.0 \
               -Djava.security.egd=file:/dev/./urandom"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
