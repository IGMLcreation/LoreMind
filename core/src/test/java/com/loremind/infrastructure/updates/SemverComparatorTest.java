package com.loremind.infrastructure.updates;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests des utilitaires semver (extraits de UpdateCheckServiceTest lors de la
 * séparation de {@link SemverComparator}).
 */
class SemverComparatorTest {

    @Test
    void parseSemver_acceptsCommonFormats() {
        assertArrayEquals(new int[]{0, 8, 0}, SemverComparator.parseSemver("0.8.0"));
        assertArrayEquals(new int[]{0, 8, 0}, SemverComparator.parseSemver("v0.8.0"));
        assertArrayEquals(new int[]{1, 0, 0}, SemverComparator.parseSemver("1.0.0"));
        assertArrayEquals(new int[]{0, 8, 0}, SemverComparator.parseSemver("0.8.0-beta.1"));
        assertArrayEquals(new int[]{0, 8, 0}, SemverComparator.parseSemver("0.8.0+build.42"));
    }

    @Test
    void parseSemver_rejectsInvalid() {
        assertNull(SemverComparator.parseSemver(null));
        assertNull(SemverComparator.parseSemver(""));
        assertNull(SemverComparator.parseSemver("latest"));
        assertNull(SemverComparator.parseSemver("stable"));
        assertNull(SemverComparator.parseSemver("0.8.0.1.2"));
        assertNull(SemverComparator.parseSemver("0.x.0"));
    }

    @Test
    void compareSemver_basic() {
        assertTrue(SemverComparator.compareSemver("0.7.2", "0.8.0") < 0);
        assertTrue(SemverComparator.compareSemver("0.8.0", "0.7.2") > 0);
        assertEquals(0, SemverComparator.compareSemver("0.8.0", "0.8.0"));
        assertEquals(0, SemverComparator.compareSemver("v0.8.0", "0.8.0"));
        assertTrue(SemverComparator.compareSemver("0.8.0", "0.10.0") < 0);
        assertTrue(SemverComparator.compareSemver("1.0.0", "0.99.99") > 0);
    }

    @Test
    void findMaxSemver_picksHighest() {
        assertEquals("0.8.0", SemverComparator.findMaxSemver(
                List.of("0.7.0", "0.7.1", "0.7.2", "0.8.0", "latest")));
        assertEquals("0.10.0", SemverComparator.findMaxSemver(
                List.of("0.8.0", "0.10.0", "0.9.5")));
        assertEquals("v1.0.0", SemverComparator.findMaxSemver(
                List.of("v0.8.0", "v1.0.0", "latest")));
    }

    @Test
    void findMaxSemver_returnsNullWhenNoValidTag() {
        assertNull(SemverComparator.findMaxSemver(List.of("latest", "stable", "main")));
        assertNull(SemverComparator.findMaxSemver(List.of()));
    }
}
