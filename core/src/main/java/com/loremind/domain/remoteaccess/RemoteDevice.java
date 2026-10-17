package com.loremind.domain.remoteaccess;

import lombok.Builder;
import lombok.Data;

import java.time.Instant;

/**
 * Appareil (tablette, téléphone) autorisé à accéder à l'application de bureau depuis
 * le réseau local, après appairage par code.
 * <p>
 * <b>Sécurité :</b> seule l'empreinte SHA-256 du jeton est conservée ; le jeton en
 * clair n'existe que dans le cookie de l'appareil. Révoquer = supprimer la ligne.
 */
@Data
@Builder
public class RemoteDevice {

    private String id;

    /** Nom choisi à l'appairage (« Tablette de Léa »), pour s'y retrouver dans la liste. */
    private String name;

    /** Empreinte SHA-256 (hex) du jeton d'appareil. */
    private String tokenHash;

    private String userAgent;

    private Instant createdAt;

    private Instant lastSeenAt;
}
