package com.loremind.domain.campaigncontext;

import lombok.Builder;
import lombok.Data;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Entité de domaine (Aggregate Root) représentant une Quête.
 *
 * <p>Niveau 1 : la Quête est une entité de PREMIÈRE CLASSE, ORTHOGONALE à
 * l'arbre Arc→Chapitre→Scène. Elle est rattachée à la Campagne (décision D2) et
 * référence des nœuds narratifs arbitraires via {@link QuestNodeRef}. Elle porte
 * ses propres conditions de déblocage (réutilise le sealed {@link Prerequisite}).</p>
 *
 * <p>Elle remplace le double rôle historique du {@code Chapter} en mode HUB :
 * {@code Prerequisite.QuestCompleted.questId} et {@code QuestProgression.questId}
 * pointent désormais une {@code Quest.id}. Entité pure, sans dépendance technique.</p>
 */
@Data
@Builder
public class Quest {

    private String id;
    private String campaignId;         // Rattachement campagne (orthogonalité, décision D2)
    /**
     * Arc de rattachement (weak ref, NULLABLE). Non nul ⇒ quête d'un ARC HUB (affichée
     * sous cet arc). Null ⇒ quête TRANSVERSE (peut couvrir plusieurs arcs ; visible dans
     * la liste « Quêtes »). Rétro-compat : les quêtes existantes ont {@code arcId=null}.
     */
    private String arcId;
    private String name;
    private String description;        // Synopsis de la quête
    private String icon;               // Clé d'icône (cf. CAMPAIGN_ICON_OPTIONS côté front)
    private int order;                 // Ordre d'affichage dans la campagne

    /**
     * Conditions de déblocage (combinées en ET). Vide => quête immédiatement AVAILABLE.
     * Réutilise le sealed {@link Prerequisite} (migré depuis Chapter en mode HUB).
     * Donnée de SCÉNARIO — l'état réel par Partie vit dans QuestProgression (Play Context).
     */
    @Builder.Default
    private List<Prerequisite> prerequisites = new ArrayList<>();

    /**
     * Nœuds narratifs (Chapitres / Scènes) traversés par la quête (N‑N, weak refs).
     */
    @Builder.Default
    private List<QuestNodeRef> nodes = new ArrayList<>();

    // Champs narratifs (repris de l'usage HUB du Chapter)
    private String gmNotes;            // Notes privées du MJ (non exportées vers FoundryVTT)
    private String playerObjectives;   // Objectifs des joueurs pour cette quête
    private String narrativeStakes;    // Enjeux narratifs dramatiques

    /** IDs des pages du Lore associées (weak cross-context references). */
    @Builder.Default
    private List<String> relatedPageIds = new ArrayList<>();

    /** IDs des images (Shared Kernel) illustrant la quête. */
    @Builder.Default
    private List<String> illustrationImageIds = new ArrayList<>();

    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
