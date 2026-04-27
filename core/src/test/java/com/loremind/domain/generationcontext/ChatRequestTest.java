package com.loremind.domain.generationcontext;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * Tests unitaires pour ChatRequest (Value Object).
 * Valide les combinaisons supportees par le metier :
 *  - chat Lore pur (loreContext seul),
 *  - chat Lore focalise (loreContext + pageContext),
 *  - chat Campagne (campaignContext),
 *  - chat Campagne focalise (campaignContext + narrativeEntity).
 */
class ChatRequestTest {

    private final List<ChatMessage> sampleMessages = List.of(
            new ChatMessage("user", "Bonjour")
    );

    @Test
    void buildLoreOnly_leavesCampaignAndEntityNull() {
        ChatRequest request = ChatRequest.builder()
                .messages(sampleMessages)
                .loreContext(new LoreStructuralContext("Ithoril", "Royaume sombre", Map.of(), List.of()))
                .build();

        assertEquals(1, request.messages().size());
        assertNotNull(request.loreContext());
        assertEquals("Ithoril", request.loreContext().loreName());
        assertNull(request.pageContext());
        assertNull(request.campaignContext());
        assertNull(request.narrativeEntity());
    }

    @Test
    void buildLoreWithPageFocus_hasBothContexts() {
        ChatRequest request = ChatRequest.builder()
                .messages(sampleMessages)
                .loreContext(new LoreStructuralContext(null, null, Map.of(), List.of()))
                .pageContext(new PageContext("Thorin", "PNJ", null, null))
                .build();

        assertNotNull(request.loreContext());
        assertNotNull(request.pageContext());
        assertEquals("Thorin", request.pageContext().title());
    }

    @Test
    void buildCampaignWithNarrativeEntity_hasBothContexts() {
        ChatRequest request = ChatRequest.builder()
                .messages(sampleMessages)
                .campaignContext(new CampaignStructuralContext(
                        "Les Ombres", "...", List.of(), List.of(), List.of()))
                .narrativeEntity(new NarrativeEntityContext(
                        "scene", "L'auberge", Map.of("location", "Taverne")))
                .build();

        assertNotNull(request.campaignContext());
        assertNotNull(request.narrativeEntity());
        assertEquals("scene", request.narrativeEntity().entityType());
        assertNull(request.loreContext());
        assertNull(request.pageContext());
    }

    @Test
    void buildMinimal_onlyRequiresMessages() {
        // Cas degenere supporte : aucun contexte, juste l'historique.
        ChatRequest request = ChatRequest.builder()
                .messages(sampleMessages)
                .build();

        assertEquals(1, request.messages().size());
        assertNull(request.loreContext());
        assertNull(request.pageContext());
        assertNull(request.campaignContext());
        assertNull(request.narrativeEntity());
    }
}
