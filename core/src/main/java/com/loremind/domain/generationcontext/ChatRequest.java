package com.loremind.domain.generationcontext;

import java.util.List;

/**
 * Object de valeur encapsulant une requête de chat streamé.
 * <p>
 * Ceci est un Value Object du Generation Context.
 * Regroupe l'historique de la conversation et les contextes structurels
 * (Lore et/ou Campagne) dont l'IA a besoin pour répondre.
 * <p>
 * Combinaisons supportées (asymétrie demandée par le métier) :
 *  - loreContext seul                         → chat Lore (page-edit / page-create)
 *  - loreContext + pageContext                → chat Lore focalisé sur une page
 *  - campaignContext (+ loreContext si liée)  → chat Campagne, voit son Lore associé
 *  - campaignContext + narrativeEntity        → chat Campagne focalisé sur arc/chapter/scene
 * <p>
 * Un chat Lore ne reçoit JAMAIS de campaignContext : un Lore ne voit pas
 * ses campagnes (asymétrie métier : la campagne est l'emprunteur du Lore,
 * pas l'inverse).
 * <p>
 * Record Java : pur domaine. Builder manuel fourni en raison des 6 champs
 * dont 5 sont nullables — l'API fluide reste plus lisible aux call sites
 * qu'un constructeur à 6 paramètres souvent à null.
 *
 * @param loreContext       Optionnel : carte structurelle du Lore. Null si campagne non liée à un Lore.
 * @param pageContext       Optionnel : contexte d'une page précise en cours d'édition (chat Lore uniquement).
 * @param campaignContext   Optionnel : carte narrative d'une Campagne (chat Campagne uniquement).
 * @param narrativeEntity   Optionnel : entité narrative en cours d'édition (arc/chapter/scene).
 * @param gameSystemContext Optionnel : règles du système de JDR de la campagne (filtrées par intent).
 *                          Null si la campagne n'a pas de GameSystem associé. Campagne uniquement au MVP.
 */
public record ChatRequest(
        List<ChatMessage> messages,
        LoreStructuralContext loreContext,
        PageContext pageContext,
        CampaignStructuralContext campaignContext,
        NarrativeEntityContext narrativeEntity,
        GameSystemContext gameSystemContext) {

    public static Builder builder() {
        return new Builder();
    }

    /** Builder fluide : permet d'omettre les contextes non pertinents. */
    public static final class Builder {
        private List<ChatMessage> messages;
        private LoreStructuralContext loreContext;
        private PageContext pageContext;
        private CampaignStructuralContext campaignContext;
        private NarrativeEntityContext narrativeEntity;
        private GameSystemContext gameSystemContext;

        private Builder() {}

        public Builder messages(List<ChatMessage> messages) {
            this.messages = messages;
            return this;
        }

        public Builder loreContext(LoreStructuralContext loreContext) {
            this.loreContext = loreContext;
            return this;
        }

        public Builder pageContext(PageContext pageContext) {
            this.pageContext = pageContext;
            return this;
        }

        public Builder campaignContext(CampaignStructuralContext campaignContext) {
            this.campaignContext = campaignContext;
            return this;
        }

        public Builder narrativeEntity(NarrativeEntityContext narrativeEntity) {
            this.narrativeEntity = narrativeEntity;
            return this;
        }

        public Builder gameSystemContext(GameSystemContext gameSystemContext) {
            this.gameSystemContext = gameSystemContext;
            return this;
        }

        public ChatRequest build() {
            return new ChatRequest(messages, loreContext, pageContext,
                    campaignContext, narrativeEntity, gameSystemContext);
        }
    }
}
