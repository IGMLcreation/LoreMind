package com.loremind.domain.campaigncontext.generation;

import java.util.List;

/**
 * Proposition de patch champ-par-champ d'une entité de scénario (Pilier A — co-création
 * « propose → applique »). Éphémère et non persistée (comme {@code CampaignImportProposal}).
 *
 * <p>Le front affiche chaque {@link FieldProposal} (diff avant/après), l'utilisateur
 * accepte/rejette champ par champ, puis renvoie CE MÊME record filtré aux seuls champs
 * acceptés au endpoint d'application. Aucun statut d'acceptation n'est porté ici : le grain
 * d'acceptation vit côté UI, l'apply ne reçoit que les champs retenus.</p>
 *
 * @param target   type d'entité ciblée : {@code "scene"} (tranche 1), à terme aussi
 *                 {@code "arc"|"chapter"|"npc"}
 * @param targetId id de l'entité DÉJÀ persistée à patcher
 * @param type     {@code "patch"} (tranche 1) ou {@code "create"} (extension future)
 * @param fields   un {@link FieldProposal} par champ proposé/accepté
 */
public record EntityFieldPatchProposal(String target, String targetId, String type, List<FieldProposal> fields) {
}
