# Runtime image for the Checkout Service. Build the reactor first on the host
# (mvn -f service-based/pom.xml package), then `docker compose build`.
FROM eclipse-temurin:21-jre
WORKDIR /app
COPY target/*-SNAPSHOT.jar app.jar
EXPOSE 8082
ENTRYPOINT ["java", "-jar", "app.jar"]
