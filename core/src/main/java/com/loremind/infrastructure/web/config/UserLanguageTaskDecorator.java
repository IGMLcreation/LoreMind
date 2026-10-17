package com.loremind.infrastructure.web.config;

import org.springframework.core.task.TaskDecorator;

/**
 * Relaie la langue de l'utilisateur ({@link UserLanguageHolder}, un ThreadLocal) du thread
 * de la requête HTTP vers le thread de fond qui exécute la tâche.
 * <p>
 * Sans lui, les traitements longs lancés dans {@code applicationTaskExecutor} (imports de PDF,
 * ateliers, chat IA en streaming…) lisaient la valeur par défaut du ThreadLocal sur leur
 * propre thread : le Brain rédigeait toujours en français, même avec l'interface en anglais.
 * Spring Boot associe automatiquement ce décorateur à l'exécuteur auto-configuré.
 */
public class UserLanguageTaskDecorator implements TaskDecorator {

    @Override
    public Runnable decorate(Runnable runnable) {
        String language = UserLanguageHolder.get(); // lu sur le thread de la requête
        return () -> {
            String previous = UserLanguageHolder.get();
            UserLanguageHolder.set(language);
            try {
                runnable.run();
            } finally {
                // Threads réutilisés par le pool : on ne laisse pas la langue fuir vers la tâche suivante.
                UserLanguageHolder.set(previous);
            }
        };
    }
}
