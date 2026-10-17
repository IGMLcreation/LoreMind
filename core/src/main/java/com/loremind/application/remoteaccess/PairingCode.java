package com.loremind.application.remoteaccess;

import java.time.Instant;

/** Code d'appairage à 6 chiffres affiché sur le PC (usage unique). */
public record PairingCode(String code, Instant expiresAt) {
}
