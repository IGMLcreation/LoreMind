package com.loremind;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * Classe principale de l'application LoreMind.
 * Point d'entrée Spring Boot qui démarre l'application.
 */
@SpringBootApplication
@EnableScheduling
public class LoreMindApplication {

    public static void main(String[] args) {
        SpringApplication.run(LoreMindApplication.class, args);
    }
}
