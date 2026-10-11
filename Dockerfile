# Stage 1: Build application with Maven and JDK 21
FROM maven:3.9-eclipse-temurin-21 AS builder
WORKDIR /workspace

# Cache Maven dependencies
COPY pom.xml .
RUN mvn dependency:go-offline -B || true

# Copy source code and build executable jar
COPY src ./src
RUN mvn clean package -DskipTests -B

# Stage 2: Minimal hardened JRE runtime
FROM eclipse-temurin:21-jre-jammy
WORKDIR /app

# Run as non-root user
RUN addgroup --system settlement && adduser --system --ingroup settlement settlement
USER settlement:settlement

COPY --from=builder /workspace/target/settlement-engine-1.0.0.jar app.jar

EXPOSE 8080

ENV JAVA_OPTS="-XX:+UseContainerSupport -XX:MaxRAMPercentage=75.0"

ENTRYPOINT ["sh", "-c", "java $JAVA_OPTS -jar app.jar"]
