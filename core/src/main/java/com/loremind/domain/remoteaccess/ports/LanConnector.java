package com.loremind.domain.remoteaccess.ports;

import java.util.List;

/**
 * Port vers le « second guichet » réseau de l'application de bureau : un connecteur
 * HTTP ouvert sur le réseau local (toutes interfaces) à côté du connecteur habituel,
 * qui reste limité à la machine (127.0.0.1).
 * <p>
 * Tout ce qui arrive par ce connecteur vient d'un autre appareil (ou de la machine
 * elle-même via son adresse réseau) et doit présenter un jeton d'appareil.
 */
public interface LanConnector {

    /**
     * Ouvre le connecteur sur ce port (le referme d'abord s'il tournait sur un autre).
     *
     * @throws IllegalStateException si le port est déjà occupé ou ne peut pas être ouvert
     */
    void start(int port);

    void stop();

    /** Port du connecteur ouvert, ou -1 s'il est fermé. */
    int runningPort();

    /** Adresses IPv4 de la machine sur le réseau local (pour composer l'URL à taper). */
    List<String> lanAddresses();
}
