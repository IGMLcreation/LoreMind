package com.loremind.application.remoteaccess;

import com.loremind.domain.remoteaccess.RemoteDevice;

import java.time.Instant;

/** Vue publique d'un appareil appairé (jamais l'empreinte du jeton). */
public record PairedDevice(String id, String name, String userAgent, Instant createdAt, Instant lastSeenAt) {

    static PairedDevice of(RemoteDevice d) {
        return new PairedDevice(d.getId(), d.getName(), d.getUserAgent(), d.getCreatedAt(), d.getLastSeenAt());
    }
}
