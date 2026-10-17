package com.loremind.infrastructure.web.config;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.core.task.TaskExecutor;

import java.util.concurrent.CompletableFuture;
import java.util.concurrent.TimeUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * La langue de l'interface suit les traitements de fond : sans ce relais, les imports de PDF
 * et les flux IA lancés dans l'exécuteur partaient toujours en français.
 */
@SpringBootTest
class UserLanguageTaskDecoratorTest {

    @Autowired @Qualifier("applicationTaskExecutor") private TaskExecutor taskExecutor;

    @AfterEach
    void clear() {
        UserLanguageHolder.clear();
    }

    @Test
    void applicationTaskExecutor_runsTasksInTheRequestLanguage() throws Exception {
        UserLanguageHolder.set("en");
        CompletableFuture<String> seen = new CompletableFuture<>();
        taskExecutor.execute(() -> seen.complete(UserLanguageHolder.get()));
        assertEquals("en", seen.get(5, TimeUnit.SECONDS));
    }

    @Test
    void decorator_restoresTheWorkerThreadLanguageAfterTheTask() {
        UserLanguageHolder.set("en");
        Runnable decorated = new UserLanguageTaskDecorator().decorate(() -> assertEquals("en", UserLanguageHolder.get()));
        UserLanguageHolder.set("fr"); // le « thread de travail » avait une autre langue
        decorated.run();
        assertEquals("fr", UserLanguageHolder.get(), "pas de fuite vers la tâche suivante du pool");
    }
}
