package com.loremind.infrastructure.transfer;

import com.loremind.domain.campaigncontext.structure.ArcType;
import com.loremind.domain.campaigncontext.quest.NodeType;
import com.loremind.domain.campaigncontext.quest.Prerequisite;
import com.loremind.domain.campaigncontext.quest.QuestNodeRef;
import com.loremind.domain.campaigncontext.structure.Room;
import com.loremind.domain.campaigncontext.structure.SceneBattlemap;
import com.loremind.domain.campaigncontext.structure.SceneBranch;

import java.util.ArrayList;
import java.util.LinkedHashMap;
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
     * Remap des valeurs d'un champ image « multi » ({@code slot → [imageId...]}, ex. galeries
     * de PNJ/pages) : chaque id d'image est réécrit via {@code imageMap}, clés (slots) inchangées.
     */
    static Map<String, List<String>> remapImageValues(Map<Long, Long> imageMap,
                                                      Map<String, List<String>> imageValues) {
        if (imageValues == null) return null;
        Map<String, List<String>> out = new LinkedHashMap<>();
        for (Map.Entry<String, List<String>> e : imageValues.entrySet()) {
            out.put(e.getKey(), remapStringList(imageMap, e.getValue()));
        }
        return out;
    }

    /**
     * Remap du cadrage d'images d'une page ({@code fieldKey → imageId → cadrage}) : seule la
     * clé INTERNE (imageId) est réécrite via {@code imageMap}, la clé externe (fieldKey) et la
     * valeur de cadrage sont inchangées. Générique sur la valeur pour éviter d'importer le type
     * de cadrage ici. Sans ce remap, le cadrage resterait indexé sous l'ancien id et l'image
     * importée perdrait son pan/zoom.
     */
    static <V> Map<String, Map<String, V>> remapImageFraming(Map<Long, Long> imageMap,
                                                             Map<String, Map<String, V>> framing) {
        if (framing == null) return null;
        Map<String, Map<String, V>> out = new LinkedHashMap<>();
        for (Map.Entry<String, Map<String, V>> outer : framing.entrySet()) {
            Map<String, V> inner = outer.getValue();
            if (inner == null) {
                out.put(outer.getKey(), null);
                continue;
            }
            Map<String, V> remappedInner = new LinkedHashMap<>();
            for (Map.Entry<String, V> e : inner.entrySet()) {
                remappedInner.put(remapStringId(imageMap, e.getKey()), e.getValue());
            }
            out.put(outer.getKey(), remappedInner);
        }
        return out;
    }

    /**
     * Remap des réfs d'images portées par les salles (Room) d'une scène : galerie
     * ({@code illustrationImageIds}) + plan ({@code mapImageId}). Room étant un value object
     * mutable (Lombok), on réécrit EN PLACE — la liste vient d'une désérialisation jetable.
     */
    static List<Room> remapRoomImages(Map<Long, Long> imageMap, List<Room> rooms) {
        if (rooms == null) return null;
        for (Room r : rooms) {
            if (r == null) continue;
            r.setIllustrationImageIds(remapStringList(imageMap, r.getIllustrationImageIds()));
            r.setMapImageId(remapStringId(imageMap, r.getMapImageId()));
        }
        return rooms;
    }

    /**
     * Remap des réfs de fichiers (StoredFile) des battlemaps d'une scène : {@code mediaFileId}
     * + {@code dataFileId} réécrits via {@code fileMap}. SceneBattlemap est un record immuable,
     * on reconstruit chaque entrée.
     */
    static List<SceneBattlemap> remapBattlemaps(Map<Long, Long> fileMap, List<SceneBattlemap> battlemaps) {
        if (battlemaps == null) return null;
        List<SceneBattlemap> out = new ArrayList<>(battlemaps.size());
        for (SceneBattlemap bm : battlemaps) {
            out.add(new SceneBattlemap(bm.label(),
                    remapStringId(fileMap, bm.mediaFileId()),
                    remapStringId(fileMap, bm.dataFileId())));
        }
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
