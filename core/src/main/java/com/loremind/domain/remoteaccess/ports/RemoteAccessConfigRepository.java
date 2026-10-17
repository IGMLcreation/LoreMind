package com.loremind.domain.remoteaccess.ports;

import com.loremind.domain.remoteaccess.RemoteAccessConfig;

import java.util.Optional;

/** Port de persistance du réglage d'accès distant (singleton). */
public interface RemoteAccessConfigRepository {

    Optional<RemoteAccessConfig> find();

    RemoteAccessConfig save(RemoteAccessConfig config);
}
