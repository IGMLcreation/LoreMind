package com.loremind.infrastructure.desktop;

import com.loremind.application.remoteaccess.RemoteAccessService;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.annotation.Profile;
import org.springframework.context.event.EventListener;
import org.springframework.stereotype.Component;

/** Mode bureau : rouvre l'accès distant au démarrage s'il avait été activé. */
@Component
@Profile("local")
public class RemoteAccessBootstrap {

    private final RemoteAccessService remoteAccessService;

    public RemoteAccessBootstrap(RemoteAccessService remoteAccessService) {
        this.remoteAccessService = remoteAccessService;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void reopenIfEnabled() {
        remoteAccessService.applySavedConfig();
    }
}
