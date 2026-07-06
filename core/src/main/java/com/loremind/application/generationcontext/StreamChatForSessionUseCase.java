package com.loremind.application.generationcontext;

import com.loremind.application.gamesystemcontext.GameSystemContextBuilder;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.gamesystemcontext.GenerationIntent;
import com.loremind.domain.generationcontext.CampaignStructuralContext;
import com.loremind.domain.generationcontext.ChatMessage;
import com.loremind.domain.generationcontext.ChatRequest;
import com.loremind.domain.generationcontext.ChatStreamCallbacks;
import com.loremind.domain.generationcontext.GameSystemContext;
import com.loremind.domain.generationcontext.LoreStructuralContext;
import com.loremind.domain.generationcontext.SessionContext;
import com.loremind.domain.generationcontext.ports.AiChatProvider;
import com.loremind.domain.playcontext.Playthrough;
import com.loremind.domain.playcontext.Session;
import com.loremind.domain.playcontext.ports.PlaythroughRepository;
import com.loremind.domain.playcontext.ports.SessionRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Use case applicatif : chat IA pendant une Session de jeu.
 * <p>
 * Orchestre la composition des contextes :
 *  1. Charge la Session puis la Campagne associée (weak reference).
 *  2. Construit le CampaignStructuralContext (carte narrative + PJ/PNJ).
 *  3. Construit le LoreStructuralContext si la campagne est liée à un Lore.
 *  4. Construit le GameSystemContext si elle a un système de JDR.
 *  5. Construit le SessionContext (journal horodaté, statut).
 *  6. Délègue au port {@link AiChatProvider} pour le streaming.
 * </p>
 *
 * <p>La conversation est éphémère (pas de persistance) : pendant une partie,
 * l'utilité est d'avoir une assistance immédiate, pas de garder un historique.
 * Le journal de session joue déjà ce rôle de mémoire persistante.</p>
 */
@Service
public class StreamChatForSessionUseCase {

    private final SessionRepository sessionRepository;
    private final PlaythroughRepository playthroughRepository;
    private final CampaignRepository campaignRepository;
    private final CampaignStructuralContextBuilder campaignContextBuilder;
    private final LoreStructuralContextBuilder loreContextBuilder;
    private final GameSystemContextBuilder gameSystemContextBuilder;
    private final SessionStructuralContextBuilder sessionContextBuilder;
    private final AiChatProvider aiChatProvider;

    public StreamChatForSessionUseCase(
            SessionRepository sessionRepository,
            PlaythroughRepository playthroughRepository,
            CampaignRepository campaignRepository,
            CampaignStructuralContextBuilder campaignContextBuilder,
            LoreStructuralContextBuilder loreContextBuilder,
            GameSystemContextBuilder gameSystemContextBuilder,
            SessionStructuralContextBuilder sessionContextBuilder,
            AiChatProvider aiChatProvider) {
        this.sessionRepository = sessionRepository;
        this.playthroughRepository = playthroughRepository;
        this.campaignRepository = campaignRepository;
        this.campaignContextBuilder = campaignContextBuilder;
        this.loreContextBuilder = loreContextBuilder;
        this.gameSystemContextBuilder = gameSystemContextBuilder;
        this.sessionContextBuilder = sessionContextBuilder;
        this.aiChatProvider = aiChatProvider;
    }

    public void execute(
            String sessionId,
            List<ChatMessage> messages,
            ChatStreamCallbacks callbacks) {

        Session session = sessionRepository.findById(sessionId)
                .orElseThrow(() -> new IllegalArgumentException("Session introuvable : " + sessionId));

        // Chaîne de résolution : Session → Playthrough → Campaign.
        Playthrough playthrough = playthroughRepository.findById(session.getPlaythroughId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Partie associée à la session introuvable : " + session.getPlaythroughId()));

        Campaign campaign = campaignRepository.findById(playthrough.getCampaignId())
                .orElseThrow(() -> new IllegalArgumentException(
                        "Campagne associée à la partie introuvable : " + playthrough.getCampaignId()));

        // Le campaign context inclut les PJ de CE Playthrough (les PJ sont par-table).
        CampaignStructuralContext campaignContext = campaignContextBuilder.build(campaign.getId(), playthrough.getId());
        LoreStructuralContext loreContext = loadLoreContextOrNull(campaign);
        GameSystemContext gameSystemContext = loadGameSystemContextOrNull(campaign);
        SessionContext sessionContext = sessionContextBuilder.build(sessionId);

        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .loreContext(loreContext)
                .campaignContext(campaignContext)
                .gameSystemContext(gameSystemContext)
                .sessionContext(sessionContext)
                .build();

        aiChatProvider.streamChat(request, callbacks);
    }

    private LoreStructuralContext loadLoreContextOrNull(Campaign campaign) {
        if (!campaign.isLinkedToLore()) return null;
        return loreContextBuilder.buildOptional(campaign.getLoreId()).orElse(null);
    }

    /**
     * Pendant une session active, on injecte les sections les plus utiles en partie
     * (combats, PNJ, mécaniques) — intent SCENE est le plus proche de ce besoin.
     */
    private GameSystemContext loadGameSystemContextOrNull(Campaign campaign) {
        if (!campaign.isLinkedToGameSystem()) return null;
        return gameSystemContextBuilder.buildOptional(campaign.getGameSystemId(), GenerationIntent.SCENE)
                .orElse(null);
    }
}
