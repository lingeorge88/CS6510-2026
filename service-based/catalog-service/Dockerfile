# Runtime image for the Catalog Service. Build the reactor first on the host
# (mvn -f service-based/pom.xml package), then `docker compose build`. The
# pre-built fat jar is copied in — fast, and keeps each image a single process.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/*-SNAPSHOT.jar app.jar
EXPOSE 8081
ENTRYPOINT ["java", "-jar", "app.jar"]
