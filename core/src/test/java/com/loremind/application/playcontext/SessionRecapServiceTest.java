package com.loremind.application.playcontext;

import com.loremind.domain.playcontext.EntryType;
import com.loremind.domain.playcontext.Session;
import com.loremind.domain.playcontext.SessionEntry;
import com.loremind.domain.playcontext.ports.SessionEntryRepository;
import com.loremind.domain.playcontext.ports.SessionRecapAssistant;
import com.loremind.domain.playcontext.ports.SessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.time.Month;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;

/**
 * Test unitaire pour SessionRecapService — sélection de la séance précédente
 * + construction du transcript envoyé au port IA.
 */
@ExtendWith(MockitoExtension.class)
class SessionRecapServiceTest {

    private static final LocalDateTime T1 = LocalDateTime.of(2026, Month.JUNE, 1, 20, 0);
    private static final LocalDateTime T2 = LocalDateTime.of(2026, Month.JUNE, 23, 20, 0);
    private static final LocalDateTime T3 = LocalDateTime.of(2026, Month.JULY, 3, 20, 0);

    @Mock private SessionRepository sessionRepository;
    @Mock private SessionEntryRepository entryRepository;
    @Mock private SessionRecapAssistant recapAssistant;

    @InjectMocks private SessionRecapService service;

    private static Session session(String id, LocalDateTime startedAt) {
        return Session.builder().id(id).name("Séance " + id).playthroughId("pt-1")
                .startedAt(startedAt).endedAt(startedAt.plusHours(3)).build();
    }

    private static SessionEntry entry(String content) {
        return SessionEntry.builder().id("e").sessionId("prev").type(EntryType.EVENT)
                .content(content).occurredAt(T2).build();
    }

    @Test
    void picksTheMostRecentSessionBeforeCurrent_andBuildsTranscript() {
        Session current = session("cur", T3);
        when(sessionRepository.findById("cur")).thenReturn(Optional.of(current));
        when(sessionRepository.findByPlaythroughId("pt-1")).thenReturn(List.of(
                session("old", T1), session("prev", T2), current));
        when(entryRepository.findBySessionId("prev")).thenReturn(List.of(
                entry("Les PJ ont sauvé le convoi."), entry("La Bulette s'est enfuie.")));
        when(recapAssistant.generateRecap(anyString(), anyString())).thenReturn("Précédemment…");

        SessionRecapService.RecapResult result = service.recapPreviousSession("cur");

        assertEquals("Précédemment…", result.recap());
        assertEquals("Séance prev", result.previousSessionName());
        ArgumentCaptor<String> transcript = ArgumentCaptor.forClass(String.class);
        verify(recapAssistant).generateRecap(transcript.capture(), anyString());
        assertTrue(transcript.getValue().contains("[EVENT] Les PJ ont sauvé le convoi."));
        assertTrue(transcript.getValue().contains("La Bulette"));
    }

    @Test
    void noPreviousSession_throwsWithClearMessage() {
        Session current = session("cur", T1);
        when(sessionRepository.findById("cur")).thenReturn(Optional.of(current));
        when(sessionRepository.findByPlaythroughId("pt-1")).thenReturn(List.of(current));

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.recapPreviousSession("cur"));
        assertTrue(ex.getMessage().contains("Aucune séance précédente"));
    }

    @Test
    void previousSessionWithEmptyJournal_throws() {
        Session current = session("cur", T3);
        when(sessionRepository.findById("cur")).thenReturn(Optional.of(current));
        when(sessionRepository.findByPlaythroughId("pt-1")).thenReturn(List.of(session("prev", T2), current));
        when(entryRepository.findBySessionId("prev")).thenReturn(List.of());

        assertThrows(IllegalArgumentException.class, () -> service.recapPreviousSession("cur"));
    }
}
