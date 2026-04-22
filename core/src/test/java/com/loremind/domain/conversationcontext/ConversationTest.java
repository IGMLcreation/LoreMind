package com.loremind.domain.conversationcontext;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests unitaires du domaine pour Conversation (agregat).
 * Valide :
 *  - le @Builder.Default sur la liste des messages (jamais null),
 *  - la preservation des deux modes d'ancrage (Lore XOR Campaign),
 *  - la preservation du focus entite optionnel (page / arc / chapter / scene).
 * <p>
 * NB : la contrainte XOR (un seul de loreId/campaignId non-null) est portee
 * par ConversationService cote application, pas par le domaine — le test
 * verifie donc juste que les deux champs sont exposes independamment.
 */
class ConversationTest {

    @Test
    void builder_initializesMessagesToEmptyList_whenNotSet() {
        Conversation conv = Conversation.builder()
                .id("conv-1")
                .title("Discussion autour de Thorin")
                .loreId("lore-1")
                .build();

        assertNotNull(conv.getMessages(), "messages ne doit jamais etre null");
        assertTrue(conv.getMessages().isEmpty());
    }

    @Test
    void builder_anchorsToLore_withOptionalEntityFocus() {
        Conversation conv = Conversation.builder()
                .loreId("lore-1")
                .entityType("page")
                .entityId("page-42")
                .build();

        assertEquals("lore-1", conv.getLoreId());
        assertEquals("page", conv.getEntityType());
        assertEquals("page-42", conv.getEntityId());
    }

    @Test
    void builder_anchorsToCampaign_withSceneFocus() {
        Conversation conv = Conversation.builder()
                .campaignId("camp-1")
                .entityType("scene")
                .entityId("sc-7")
                .build();

        assertEquals("camp-1", conv.getCampaignId());
        assertEquals("scene", conv.getEntityType());
    }

    @Test
    void builder_preservesProvidedMessages() {
        ConversationMessage m1 = ConversationMessage.builder().role("user").content("hello").build();
        ConversationMessage m2 = ConversationMessage.builder().role("assistant").content("hi").build();

        Conversation conv = Conversation.builder()
                .messages(List.of(m1, m2))
                .build();

        assertEquals(2, conv.getMessages().size());
        assertEquals("user", conv.getMessages().get(0).getRole());
    }

    @Test
    void noArgsConstructor_createsConversationWithNullMessages() {
        // @NoArgsConstructor bypass le builder → on accepte que messages soit null
        // dans ce cas (cas de reconstruction JPA avant hydratation).
        Conversation conv = new Conversation();
        assertEquals(null, conv.getId());
    }
}
