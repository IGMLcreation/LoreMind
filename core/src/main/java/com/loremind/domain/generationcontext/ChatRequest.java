package com.loremind.domain.generationcontext;

import lombok.Builder;
import lombok.Value;

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
 */
@Value
@Builder
public class ChatRequest {

    List<ChatMessage> messages;

    /** Optionnel : carte structurelle du Lore. Null si campagne non liée à un Lore. */
    LoreStructuralContext loreContext;

    /** Optionnel : contexte d'une page précise en cours d'édition (chat Lore uniquement). */
    PageContext pageContext;

    /** Optionnel : carte narrative d'une Campagne (chat Campagne uniquement). */
    CampaignStructuralContext campaignContext;

    /** Optionnel : entité narrative en cours d'édition (arc/chapter/scene). */
    NarrativeEntityContext narrativeEntity;
}
