package org.afritechinnovations.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.ApplicationListener;
import org.springframework.context.annotation.Profile;
import org.springframework.core.env.Environment;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;

/**
 * Exporte automatiquement le contrat OpenAPI (JSON) dans
 * src/main/resources/contract/openapi.json à chaque démarrage de l'application,
 * afin qu'il soit toujours à jour et importable dans Postman.
 * Actif uniquement en profil "local" pour ne pas s'exécuter en dev/prod.
 */
@Component
@Profile("local")
@Slf4j
public class OpenApiContractExporter implements ApplicationListener<ApplicationReadyEvent> {

    private final Environment environment;

    public OpenApiContractExporter(Environment environment) {
        this.environment = environment;
    }

    @Override
    public void onApplicationEvent(ApplicationReadyEvent event) {
        String port = environment.getProperty("local.server.port", environment.getProperty("server.port", "8080"));
        String url = "http://localhost:" + port + "/v3/api-docs";

        try {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> response = restTemplate.getForEntity(url, String.class);

            Path contractDir = Path.of("src", "main", "resources", "contract");
            Files.createDirectories(contractDir);
            Path contractFile = contractDir.resolve("openapi.json");
            Files.writeString(contractFile, response.getBody(), StandardCharsets.UTF_8);

            log.info("Contrat OpenAPI exporté dans {}", contractFile.toAbsolutePath());
        } catch (IOException e) {
            log.warn("Impossible d'écrire le contrat OpenAPI sur disque : {}", e.getMessage());
        } catch (Exception e) {
            log.warn("Impossible de récupérer le contrat OpenAPI depuis {} : {}", url, e.getMessage());
        }
    }
}
