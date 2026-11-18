FROM maven:3.9-eclipse-temurin-21 AS build
WORKDIR /build
COPY pom.xml .
RUN mvn --batch-mode --no-transfer-progress dependency:go-offline
COPY src ./src
RUN mvn --batch-mode --no-transfer-progress package -DskipTests

FROM eclipse-temurin:21-jre
# Run as a non-root user; /app/data holds the H2 file when no external database is configured.
RUN useradd --system --uid 1001 app && mkdir -p /app/data && chown -R app /app
WORKDIR /app
USER app
COPY --from=build /build/target/url-shortener-1.0.0.jar /app/app.jar
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
