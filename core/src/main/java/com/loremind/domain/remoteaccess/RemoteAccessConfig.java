package com.loremind.domain.remoteaccess;

/**
 * Réglage de l'accès distant (singleton) : activé ou non, et port réseau ouvert
 * sur le réseau local quand il l'est.
 *
 * @param enabled accès depuis le réseau local autorisé
 * @param port    port réseau dédié (distinct du port local de l'application)
 */
public record RemoteAccessConfig(boolean enabled, int port) {

    /** Port proposé par défaut : libre en pratique, et distinct du 8080 local. */
    public static final int DEFAULT_PORT = 8765;

    public static RemoteAccessConfig disabledByDefault() {
        return new RemoteAccessConfig(false, DEFAULT_PORT);
    }
}
