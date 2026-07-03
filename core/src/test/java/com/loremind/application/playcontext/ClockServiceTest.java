package com.loremind.application.playcontext;

import com.loremind.domain.playcontext.Clock;
import com.loremind.domain.playcontext.ClockTrigger;
import com.loremind.domain.playcontext.ports.ClockRepository;
import com.loremind.domain.playcontext.ports.PlaythroughRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test unitaire de ClockService : bornage de advance/regress, clamp des segments, validations.
 */
@ExtendWith(MockitoExtension.class)
class ClockServiceTest {

    @Mock private ClockRepository clockRepository;
    @Mock private PlaythroughRepository playthroughRepository;
    @InjectMocks private ClockService service;

    @Test
    void advance_incrementsByOne() {
        stubFind("1", Clock.builder().id("1").segments(6).filled(2).build());
        assertEquals(3, service.advance("1").getFilled());
    }

    @Test
    void advance_doesNotExceedSegments() {
        stubFind("1", Clock.builder().id("1").segments(4).filled(4).build());
        assertEquals(4, service.advance("1").getFilled());
    }

    @Test
    void regress_doesNotGoBelowZero() {
        stubFind("1", Clock.builder().id("1").segments(4).filled(0).build());
        assertEquals(0, service.regress("1").getFilled());
    }

    @Test
    void create_clampsSegmentsAndStartsEmpty() {
        when(playthroughRepository.existsById("pt")).thenReturn(true);
        when(clockRepository.findByPlaythroughId("pt")).thenReturn(List.of());
        when(clockRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        Clock out = service.create("pt", "  Famine  ", "Le village meurt", 0, ClockTrigger.NONE, null, null); // 0 -> clamp à 1
        assertEquals(1, out.getSegments());
        assertEquals(0, out.getFilled());
        assertEquals("Famine", out.getName()); // trim
        assertEquals(0, out.getOrder());
    }

    @Test
    void create_unknownPlaythrough_throws() {
        when(playthroughRepository.existsById("x")).thenReturn(false);
        assertThrows(IllegalArgumentException.class, () -> service.create("x", "C", null, 4, ClockTrigger.NONE, null, null));
    }

    @Test
    void create_blankName_throws() {
        when(playthroughRepository.existsById("pt")).thenReturn(true);
        assertThrows(IllegalArgumentException.class, () -> service.create("pt", "  ", null, 4, ClockTrigger.NONE, null, null));
    }

    @Test
    void update_reclampsFilledWhenShrinking() {
        stubFind("1", Clock.builder().id("1").segments(8).filled(7).name("X").build());
        Clock out = service.update("1", "X", null, 4, ClockTrigger.NONE, null, null); // réduit à 4 -> filled re-borné à 4
        assertEquals(4, out.getSegments());
        assertEquals(4, out.getFilled());
    }

    @Test
    void advance_missing_throws() {
        when(clockRepository.findById("nope")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.advance("nope"));
    }

    // --- Avancement automatique (co-MJ) ---

    @Test
    void onFlagRaised_advancesMatchingClock() {
        Clock c = Clock.builder().id("1").playthroughId("pt").segments(4).filled(1)
                .triggerType(ClockTrigger.FLAG_SET).triggerRef("porte").build();
        when(clockRepository.findByPlaythroughId("pt")).thenReturn(List.of(c));
        when(clockRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        service.onFlagRaised("pt", "porte");
        assertEquals(2, c.getFilled());
    }

    @Test
    void onFlagRaised_ignoresOtherFlagAndOtherTriggerType() {
        Clock other = Clock.builder().id("1").playthroughId("pt").segments(4).filled(0)
                .triggerType(ClockTrigger.FLAG_SET).triggerRef("autre").build();
        Clock session = Clock.builder().id("2").playthroughId("pt").segments(4).filled(0)
                .triggerType(ClockTrigger.SESSION_ENDED).build();
        when(clockRepository.findByPlaythroughId("pt")).thenReturn(List.of(other, session));
        service.onFlagRaised("pt", "porte");
        assertEquals(0, other.getFilled());
        assertEquals(0, session.getFilled());
        verify(clockRepository, never()).save(any());
    }

    @Test
    void onSessionEnded_advancesSessionClocks_notThoseAlreadyFull() {
        Clock s = Clock.builder().id("1").playthroughId("pt").segments(6).filled(5)
                .triggerType(ClockTrigger.SESSION_ENDED).build();
        Clock full = Clock.builder().id("2").playthroughId("pt").segments(2).filled(2)
                .triggerType(ClockTrigger.SESSION_ENDED).build();
        when(clockRepository.findByPlaythroughId("pt")).thenReturn(List.of(s, full));
        when(clockRepository.save(any())).thenAnswer(i -> i.getArgument(0));
        service.onSessionEnded("pt");
        assertEquals(6, s.getFilled());
        assertEquals(2, full.getFilled());
    }

    private void stubFind(String id, Clock clock) {
        when(clockRepository.findById(id)).thenReturn(Optional.of(clock));
        when(clockRepository.save(any())).thenAnswer(i -> i.getArgument(0));
    }
}
