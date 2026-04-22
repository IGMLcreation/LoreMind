package com.loremind.domain.conversationcontext;

import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests unitaires pour ConversationMessage.
 * Entite persistee (@Data mutable) : distinct du record ChatMessage du
 * generationcontext — ici on ajoute id et horodatage pour l'affichage.
 */
class ConversationMessageTest {

    @Test
    void builder_preservesAllFields() {
        LocalDateTime now = LocalDateTime.now();
        ConversationMessage msg = ConversationMessage.builder()
                .id("msg-1")
                .role("user")
                .content("Salut")
                .createdAt(now)
                .build();

        assertEquals("msg-1", msg.getId());
        assertEquals("user", msg.getRole());
        assertEquals("Salut", msg.getContent());
        assertEquals(now, msg.getCreatedAt());
    }

    @Test
    void noArgsConstructor_yieldsEmptyMessage() {
        ConversationMessage msg = new ConversationMessage();
        assertNull(msg.getId());
        assertNull(msg.getRole());
        assertNull(msg.getContent());
    }

    @Test
    void allArgsConstructor_populatesEveryField() {
        LocalDateTime now = LocalDateTime.now();
        ConversationMessage msg = new ConversationMessage("m-1", "assistant", "Reponse", now);
        assertNotNull(msg);
        assertEquals("assistant", msg.getRole());
        assertEquals("Reponse", msg.getContent());
    }

    @Test
    void setters_mutateFields() {
        // @Data genere les setters : verifier la mutabilite attendue.
        ConversationMessage msg = new ConversationMessage();
        msg.setRole("system");
        msg.setContent("system prompt");
        assertEquals("system", msg.getRole());
        assertEquals("system prompt", msg.getContent());
    }
}
