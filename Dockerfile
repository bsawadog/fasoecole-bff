FROM eclipse-temurin:21-jre-jammy
WORKDIR /app
RUN groupadd --system app && useradd --system --gid app app
COPY --chown=app:app target/fasoecole-bff-1.0-SNAPSHOT.jar app.jar
COPY --chown=app:app infra/dev/certs/eu-west-3-bundle.pem /app/certs/eu-west-3-bundle.pem
USER app
EXPOSE 8080
ENTRYPOINT ["java", "-jar", "/app/app.jar"]
