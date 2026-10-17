package com.loremind.application.remoteaccess;

import java.util.List;

/**
 * État de l'accès distant, pour l'écran Paramètres du PC.
 *
 * @param enabled   accès activé (réglage enregistré)
 * @param port      port réseau dédié
 * @param running   connecteur réseau effectivement ouvert
 * @param urls      adresses à taper sur la tablette (une par interface réseau locale)
 * @param lastError dernière erreur d'ouverture (ex. port occupé au démarrage), ou null
 * @param devices   appareils appairés
 */
public record RemoteAccessStatus(
        boolean enabled,
        int port,
        boolean running,
        List<String> urls,
        String lastError,
        List<PairedDevice> devices
) {
}
