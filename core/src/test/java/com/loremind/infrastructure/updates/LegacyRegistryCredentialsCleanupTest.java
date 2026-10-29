package com.loremind.infrastructure.updates;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Files;
import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

class LegacyRegistryCredentialsCleanupTest {

    @Test
    void removesTheOldGhcrCredentials(@TempDir Path dir) throws Exception {
        Path config = dir.resolve("config.json");
        Files.writeString(config, "{\"auths\":{\"ghcr.io\":{\"auth\":\"dXNlcjpwYXQ=\"}}}");

        assertTrue(new LegacyRegistryCredentialsCleanup(config.toString()).cleanup());
        assertFalse(Files.exists(config));
    }

    @Test
    void doesNothingOnRecentInstalls(@TempDir Path dir) {
        assertFalse(new LegacyRegistryCredentialsCleanup("").cleanup());
        assertFalse(new LegacyRegistryCredentialsCleanup(dir.resolve("config.json").toString()).cleanup());
    }
}
