# Two-stage build: compile with the JDK, ship with the smaller JRE.
# The runtime image is ~250 MB instead of ~600 MB and contains no
# build toolchain, so the attack surface in production is smaller.

# ---- Stage 1: build ----
FROM eclipse-temurin:17-jdk AS builder
WORKDIR /app

# Copy just enough to resolve dependencies first. This layer is cached
# until pom.xml changes, so day-to-day code edits don't re-download deps.
COPY .mvn .mvn
COPY mvnw pom.xml ./
RUN chmod +x mvnw && ./mvnw -B -ntp dependency:go-offline

# Now copy the source and build the jar.
COPY src src
RUN ./mvnw -B -ntp clean package -DskipTests

# ---- Stage 2: runtime ----
FROM eclipse-temurin:17-jre
WORKDIR /app

# Drop the boot-INF jar from the builder stage; Spring Boot's repackaged
# jar is self-contained.
COPY --from=builder /app/target/backend-0.0.1-SNAPSHOT.jar app.jar

# Railway injects PORT; Spring Boot reads it via server.port in
# application.properties. Locally it falls back to 8080.
EXPOSE 8080

ENTRYPOINT ["java", "-jar", "/app/app.jar"]
