package com.loremind.infrastructure.web.config;

import com.loremind.domain.shared.ports.CurrentLanguage;
import org.springframework.stereotype.Component;

/** Langue courante lue dans {@link UserLanguageHolder} (requête HTTP, ou tâche de fond décorée). */
@Component
public class UserLanguageCurrentLanguage implements CurrentLanguage {

    @Override
    public String get() {
        return UserLanguageHolder.get();
    }
}
