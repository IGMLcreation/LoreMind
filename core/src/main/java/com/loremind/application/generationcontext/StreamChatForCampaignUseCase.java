package com.loremind.application.generationcontext;

import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.generationcontext.CampaignStructuralContext;
import com.loremind.domain.generationcontext.ChatMessage;
import com.loremind.domain.generationcontext.ChatRequest;
import com.loremind.domain.generationcontext.LoreStructuralContext;
import com.loremind.domain.generationcontext.NarrativeEntityContext;
import com.loremind.domain.generationcontext.ports.AiChatProvider;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.function.Consumer;

/**
 * Use case applicatif : chat conversationnel pour une Campagne avec Structural Context.
 * <p>
 * Orchestre :
 *  1. Chargement de la carte narrative de la Campagne (arcs → chapitres → scènes).
 *  2. Si la Campagne est liée à un Lore (`loreId`), chargement également de
 *     la carte du Lore associé (asymétrie métier : Campagne voit son Lore).
 *  3. Si une entité narrative précise est ciblée (arc/chapter/scene en cours
 *     d'édition), focalisation via `NarrativeEntityContext`.
 *  4. Délégation au port `AiChatProvider` pour le streaming token par token.
 * <p>
 * Zéro persistance : la conversation est éphémère (responsabilité du frontend).
 */
@Service
public class StreamChatForCampaignUseCase {

    private final CampaignRepository campaignRepository;
    private final CampaignStructuralContextBuilder campaignContextBuilder;
    private final LoreStructuralContextBuilder loreContextBuilder;
    private final NarrativeEntityContextBuilder narrativeEntityContextBuilder;
    private final AiChatProvider aiChatProvider;

    public StreamChatForCampaignUseCase(
            CampaignRepository campaignRepository,
            CampaignStructuralContextBuilder campaignContextBuilder,
            LoreStructuralContextBuilder loreContextBuilder,
            NarrativeEntityContextBuilder narrativeEntityContextBuilder,
            AiChatProvider aiChatProvider) {
        this.campaignRepository = campaignRepository;
        this.campaignContextBuilder = campaignContextBuilder;
        this.loreContextBuilder = loreContextBuilder;
        this.narrativeEntityContextBuilder = narrativeEntityContextBuilder;
        this.aiChatProvider = aiChatProvider;
    }

    /**
     * Streame la réponse du LLM pour la Campagne donnée.
     * <p>
     * Méthode bloquante : retourne une fois le stream terminé (onComplete ou onError).
     * L'appelant (controller SSE) doit l'exécuter dans un thread dédié.
     *
     * @param campaignId obligatoire — la campagne concernée
     * @param entityType optionnel ("arc"|"chapter"|"scene") — si fourni avec entityId,
     *                   focalise l'IA sur l'entité narrative en cours d'édition.
     * @param entityId   optionnel — ID de l'entité si `entityType` est fourni
     * @throws IllegalArgumentException si la Campagne (ou l'entité ciblée) est introuvable
     */
    public void execute(
            String campaignId,
            String entityType,
            String entityId,
            List<ChatMessage> messages,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError) {

        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Campagne non trouvée avec l'ID: " + campaignId));

        CampaignStructuralContext campaignContext = campaignContextBuilder.build(campaignId);
        LoreStructuralContext loreContext = loadLinkedLoreContextOrNull(campaign);
        NarrativeEntityContext narrativeEntity = buildNarrativeEntityOrNull(entityType, entityId);

        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .loreContext(loreContext)
                .campaignContext(campaignContext)
                .narrativeEntity(narrativeEntity)
                .build();

        aiChatProvider.streamChat(request, onToken, onComplete, onError);
    }

    /**
     * Charge le LoreStructuralContext si la campagne est liée ET que le Lore
     * existe encore (cas dégradé : loreId pointant sur un Lore supprimé →
     * on continue sans contexte Lore plutôt que d'échouer).
     */
    private LoreStructuralContext loadLinkedLoreContextOrNull(Campaign campaign) {
        if (!campaign.isLinkedToLore()) return null;
        return loreContextBuilder.buildOptional(campaign.getLoreId()).orElse(null);
    }

    private NarrativeEntityContext buildNarrativeEntityOrNull(String entityType, String entityId) {
        if (entityType == null || entityType.isBlank()) return null;
        if (entityId == null || entityId.isBlank()) return null;
        return narrativeEntityContextBuilder.build(entityType, entityId);
    }
}
