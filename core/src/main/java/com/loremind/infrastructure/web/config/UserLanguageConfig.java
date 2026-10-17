package com.loremind.infrastructure.web.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.task.TaskDecorator;

/**
 * Langue de l'utilisateur dans les tâches de fond : Spring Boot applique ce décorateur unique
 * à l'exécuteur auto-configuré ({@code applicationTaskExecutor}) utilisé par les flux SSE.
 */
@Configuration
public class UserLanguageConfig {

    @Bean
    public TaskDecorator userLanguageTaskDecorator() {
        return new UserLanguageTaskDecorator();
    }
}
