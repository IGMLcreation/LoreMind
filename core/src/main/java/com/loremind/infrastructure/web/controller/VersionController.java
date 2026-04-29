package com.loremind.infrastructure.web.controller;

import org.springframework.boot.info.BuildProperties;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

/**
 * Endpoint public exposant la version courante du binaire.
 * <p>
 * Consomme par le frontend pour detecter qu'une mise a jour a ete deployee
 * pendant qu'un onglet utilisateur etait deja ouvert : si la version polled
 * differe de celle observee au boot, l'UI affiche un bandeau "rechargez".
 * <p>
 * Volontairement public (pas d'auth) : la version est deja exposee dans le
 * JAR / l'image Docker, aucun risque de leak.
 */
@RestController
@RequestMapping("/api/version")
public class VersionController {

    private final String version;

    public VersionController(@Nullable BuildProperties buildProperties) {
        this.version = buildProperties != null ? buildProperties.getVersion() : "dev";
    }

    @GetMapping
    public Map<String, String> getVersion() {
        return Map.of("version", version);
    }
}
