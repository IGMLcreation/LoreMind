package com.loremind.domain.remoteaccess.ports;

import com.loremind.domain.remoteaccess.RemoteDevice;

import java.time.Instant;
import java.util.List;
import java.util.Optional;

/** Port de persistance des appareils appairés. */
public interface RemoteDeviceRepository {

    RemoteDevice save(RemoteDevice device);

    Optional<RemoteDevice> findByTokenHash(String tokenHash);

    List<RemoteDevice> findAll();

    void touch(String id, Instant lastSeenAt);

    boolean deleteById(String id);
}
