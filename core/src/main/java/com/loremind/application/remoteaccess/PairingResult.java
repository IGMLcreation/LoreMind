package com.loremind.application.remoteaccess;

/**
 * Résultat d'un appairage réussi.
 *
 * @param token  jeton d'appareil en clair : à poser en cookie, jamais renvoyé dans le corps
 * @param device l'appareil enregistré
 */
public record PairingResult(String token, PairedDevice device) {
}
