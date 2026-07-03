package com.loremind.application.playcontext;

import com.loremind.domain.playcontext.Session;
import com.loremind.domain.playcontext.ports.PlaythroughRepository;
import com.loremind.domain.playcontext.ports.SessionEntryRepository;
import com.loremind.domain.playcontext.ports.SessionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test unitaire pour SessionService — scène courante épinglée (mode cockpit).
 * Le cycle de vie (start/end/rename) est couvert par les tests d'intégration controller.
 */
@ExtendWith(MockitoExtension.class)
class SessionServiceTest {

    @Mock private SessionRepository sessionRepository;
    @Mock private SessionEntryRepository entryRepository;
    @Mock private PlaythroughRepository playthroughRepository;
    @Mock private ClockService clockService;

    @InjectMocks private SessionService service;

    @Test
    void setCurrentScene_pinsScene() {
        Session session = Session.builder().id("s-1").name("S").playthroughId("pt-1").build();
        when(sessionRepository.findById("s-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));

        Session updated = service.setCurrentScene("s-1", "scene-42");

        assertEquals("scene-42", updated.getCurrentSceneId());
    }

    @Test
    void setCurrentScene_blankOrNull_unpins() {
        Session session = Session.builder().id("s-1").name("S").currentSceneId("scene-42").build();
        when(sessionRepository.findById("s-1")).thenReturn(Optional.of(session));
        when(sessionRepository.save(any(Session.class))).thenAnswer(inv -> inv.getArgument(0));

        assertNull(service.setCurrentScene("s-1", null).getCurrentSceneId());
        session.setCurrentSceneId("scene-42");
        assertNull(service.setCurrentScene("s-1", "  ").getCurrentSceneId());
    }

    @Test
    void setCurrentScene_unknownSession_throws() {
        when(sessionRepository.findById("nope")).thenReturn(Optional.empty());
        assertThrows(IllegalArgumentException.class, () -> service.setCurrentScene("nope", "scene-1"));
        verify(sessionRepository, never()).save(any());
    }
}
