package com.loremind.infrastructure.transfer;

import com.loremind.domain.campaigncontext.ArcType;
import com.loremind.domain.campaigncontext.NodeType;
import com.loremind.domain.campaigncontext.Prerequisite;
import com.loremind.domain.campaigncontext.QuestNodeRef;
import com.loremind.domain.campaigncontext.SceneBranch;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;

/**
 * Logique de remapping des identifiants pour l'import en mode FUSION
 * (cf. {@link ImportService}).
 * <p>
 * Fonctions PURES (sans état ni I/O) : chaque entité importée reçoit un nouvel id,
 * et toutes les références — FK {@code Long} comme refs faibles stockées en
 * {@code String} — sont réécrites {@code oldId → newId} via les maps fournies.
 * Une référence absente de la map est CONSERVÉE telle quelle (choix : ne jamais
 * perdre d'info, ne jamais planter sur une référence hors périmètre d'export).
 */
final class IdRemapper {

    private IdRemapper() {
    }

    /** Remap d'une FK Long : si absente de la map, on garde l'ancienne valeur ; {@code null → null}. */
    static Long remapId(Map<Long, Long> map, Long oldId) {
        if (oldId == null) return null;
        return map.getOrDefault(oldId, oldId);
    }

    /** Remap d'un id stocké en String ({@code "oldLong" → "newLong"}) via une map Long. */
    static String remapStringId(Map<Long, Long> map, String oldId) {
        if (oldId == null || oldId.isBlank()) return oldId;
        try {
            Long newId = map.get(Long.parseLong(oldId.trim()));
            return newId != null ? String.valueOf(newId) : oldId;
        } catch (NumberFormatException ex) {
            return oldId; // pas un Long : on laisse tel quel
        }
    }

    static List<String> remapStringList(Map<Long, Long> map, List<String> ids) {
        if (ids == null) return null;
        List<String> out = new ArrayList<>(ids.size());
        for (String id : ids) out.add(remapStringId(map, id));
        return out;
    }

    /**
     * Remappe {@code QuestCompleted.questId} via {@code idMap}. L'appelant fournit la map
     * sémantiquement correcte selon le contexte : {@code questMap} pour des prérequis de
     * Quête (v2), {@code chapterMap} pour des prérequis de Chapitre legacy (v1).
     */
    static List<Prerequisite> remapPrerequisites(Map<Long, Long> idMap, List<Prerequisite> prereqs) {
        if (prereqs == null) return null;
        List<Prerequisite> out = new ArrayList<>(prereqs.size());
        for (Prerequisite p : prereqs) {
            if (p instanceof Prerequisite.QuestCompleted qc) {
                out.add(new Prerequisite.QuestCompleted(remapStringId(idMap, qc.questId())));
            } else {
                out.add(p); // FlagSet / SessionReached : inchangés
            }
        }
        return out;
    }

    /** Remap des nœuds d'une quête : nodeId via {@code sceneMap} (SCENE) ou {@code chapterMap} (CHAPTER). */
    static List<QuestNodeRef> remapQuestNodes(Map<Long, Long> chapterMap, Map<Long, Long> sceneMap,
                                              List<QuestNodeRef> nodes) {
        if (nodes == null) return null;
        List<QuestNodeRef> out = new ArrayList<>(nodes.size());
        for (QuestNodeRef n : nodes) {
            Map<Long, Long> map = (n.nodeType() == NodeType.SCENE) ? sceneMap : chapterMap;
            out.add(new QuestNodeRef(n.nodeType(), remapStringId(map, n.nodeId()), n.order()));
        }
        return out;
    }

    static List<SceneBranch> remapBranches(Map<Long, Long> sceneMap, List<SceneBranch> branches) {
        if (branches == null) return null;
        List<SceneBranch> out = new ArrayList<>(branches.size());
        for (SceneBranch b : branches) {
            out.add(new SceneBranch(b.label(), remapStringId(sceneMap, b.targetSceneId()), b.condition(), b.kind()));
        }
        return out;
    }

    /** Parse un {@link ArcType} tolérant : type inconnu ou {@code null} → {@code LINEAR}. */
    static ArcType parseArcType(String type) {
        if (type == null) return ArcType.LINEAR;
        try {
            return ArcType.valueOf(type);
        } catch (IllegalArgumentException ex) {
            return ArcType.LINEAR;
        }
    }
}
