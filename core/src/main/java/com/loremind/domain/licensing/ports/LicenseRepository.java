package com.loremind.domain.licensing.ports;

import com.loremind.domain.licensing.License;

import java.util.Optional;

/**
 * Port de sortie pour la persistance de la licence installee.
 * <p>
 * Une seule licence par instance ({@code id = "current"} par convention).
 */
public interface LicenseRepository {

    Optional<License> findCurrent();

    License save(License license);

    void deleteCurrent();
}
