package com.loremind.domain.generationcontext;

import org.junit.jupiter.api.Test;

import java.util.List;

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
                .loreContext(LoreStructuralContext.builder()
                        .loreName("Ithoril")
                        .loreDescription("Royaume sombre")
                        .folders(java.util.Map.of())
                        .build())
                .build();

        assertEquals(1, request.getMessages().size());
        assertNotNull(request.getLoreContext());
        assertEquals("Ithoril", request.getLoreContext().getLoreName());
        assertNull(request.getPageContext());
        assertNull(request.getCampaignContext());
        assertNull(request.getNarrativeEntity());
    }

    @Test
    void buildLoreWithPageFocus_hasBothContexts() {
        ChatRequest request = ChatRequest.builder()
                .messages(sampleMessages)
                .loreContext(LoreStructuralContext.builder().folders(java.util.Map.of()).build())
                .pageContext(PageContext.builder()
                        .title("Thorin")
                        .templateName("PNJ")
                        .build())
                .build();

        assertNotNull(request.getLoreContext());
        assertNotNull(request.getPageContext());
        assertEquals("Thorin", request.getPageContext().getTitle());
    }

    @Test
    void buildCampaignWithNarrativeEntity_hasBothContexts() {
        ChatRequest request = ChatRequest.builder()
                .messages(sampleMessages)
                .campaignContext(CampaignStructuralContext.builder()
                        .campaignName("Les Ombres")
                        .campaignDescription("...")
                        .build())
                .narrativeEntity(NarrativeEntityContext.builder()
                        .entityType("scene")
                        .title("L'auberge")
                        .fields(java.util.Map.of("location", "Taverne"))
                        .build())
                .build();

        assertNotNull(request.getCampaignContext());
        assertNotNull(request.getNarrativeEntity());
        assertEquals("scene", request.getNarrativeEntity().getEntityType());
        assertNull(request.getLoreContext());
        assertNull(request.getPageContext());
    }

    @Test
    void buildMinimal_onlyRequiresMessages() {
        // Cas degenere supporte : aucun contexte, juste l'historique.
        ChatRequest request = ChatRequest.builder()
                .messages(sampleMessages)
                .build();

        assertEquals(1, request.getMessages().size());
        assertNull(request.getLoreContext());
        assertNull(request.getPageContext());
        assertNull(request.getCampaignContext());
        assertNull(request.getNarrativeEntity());
    }
}
