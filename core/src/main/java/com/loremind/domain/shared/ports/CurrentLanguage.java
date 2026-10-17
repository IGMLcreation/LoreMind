package com.loremind.domain.shared.ports;

/**
 * Port : langue de l'interface de l'utilisateur courant ({@code fr} ou {@code en}), pour les
 * contenus qui existent en deux langues (règles d'un système de jeu). Implémenté côté web.
 */
public interface CurrentLanguage {

    /** Code court de la langue courante ({@code fr} par défaut). */
    String get();
}
