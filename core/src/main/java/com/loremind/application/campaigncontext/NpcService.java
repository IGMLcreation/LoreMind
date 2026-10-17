package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.bestiary.Npc;
import com.loremind.domain.campaigncontext.bestiary.NpcStory;
import com.loremind.domain.shared.ReorderSupport;
import com.loremind.domain.campaigncontext.ports.NpcRepository;
import org.springframework.stereotype.Service;

import java.util.*;

/**
 * Service d'application pour les fiches de PNJ (campagne).
 */
@Service
public class NpcService {

    private final NpcRepository npcRepository;

    public NpcService(NpcRepository npcRepository) {
        this.npcRepository = npcRepository;
    }

    public record NpcData(
            String name,
            String portraitImageId,
            String headerImageId,
            Map<String, String> values,
            Map<String, List<String>> imageValues,
            Map<String, Map<String, String>> keyValueValues,
            String campaignId,
            List<String> relatedPageIds,
            String folder,
            Integer order,
            /** Rôle dans l'histoire ; null = inchangé (mise à jour) / non renseigné (création). */
            NpcStory story
    ) {
        public NpcData(String name, String portraitImageId, String headerImageId, Map<String, String> values,
                       Map<String, List<String>> imageValues, Map<String, Map<String, String>> keyValueValues,
                       String campaignId, List<String> relatedPageIds, String folder, Integer order) {
            this(name, portraitImageId, headerImageId, values, imageValues, keyValueValues, campaignId,
                    relatedPageIds, folder, order, null);
        }
    }

    public Npc createNpc(NpcData data) {
        int order = data.order() != null
                ? data.order()
                : nextOrderFor(data.campaignId());
        Npc npc = Npc.builder()
                .name(data.name())
                .portraitImageId(data.portraitImageId())
                .headerImageId(data.headerImageId())
                .values(data.values() != null ? new HashMap<>(data.values()) : new HashMap<>())
                .imageValues(data.imageValues() != null ? new HashMap<>(data.imageValues()) : new HashMap<>())
                .keyValueValues(data.keyValueValues() != null ? new HashMap<>(data.keyValueValues()) : new HashMap<>())
                .campaignId(data.campaignId())
                .relatedPageIds(data.relatedPageIds() != null ? new ArrayList<>(data.relatedPageIds()) : new ArrayList<>())
                .folder(normalizeFolder(data.folder()))
                .order(order)
                .story(cleanStory(data.story()))
                .build();
        return npcRepository.save(npc);
    }

    public Optional<Npc> getNpcById(String id) {
        return npcRepository.findById(id);
    }

    public List<Npc> getNpcsByCampaignId(String campaignId) {
        return npcRepository.findByCampaignId(campaignId);
    }

    public Npc updateNpc(String id, NpcData data) {
        Npc existing = npcRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Npc non trouvé avec l'ID: " + id));
        existing.setName(data.name());
        existing.setPortraitImageId(data.portraitImageId());
        existing.setHeaderImageId(data.headerImageId());
        existing.setValues(data.values() != null ? new HashMap<>(data.values()) : new HashMap<>());
        existing.setImageValues(data.imageValues() != null ? new HashMap<>(data.imageValues()) : new HashMap<>());
        existing.setKeyValueValues(data.keyValueValues() != null ? new HashMap<>(data.keyValueValues()) : new HashMap<>());
        existing.setRelatedPageIds(data.relatedPageIds() != null ? new ArrayList<>(data.relatedPageIds()) : new ArrayList<>());
        existing.setFolder(normalizeFolder(data.folder()));
        if (data.story() != null) {
            existing.setStory(cleanStory(data.story()));
        }
        if (data.order() != null) {
            existing.setOrder(data.order());
        }
        return npcRepository.save(existing);
    }

    /** Fiche nettoyée, ou null si elle est vide (rien à stocker). */
    private static NpcStory cleanStory(NpcStory story) {
        if (story == null) return null;
        NpcStory clean = story.normalized();
        return clean.hasContent() ? clean : null;
    }

    public void deleteNpc(String id) {
        npcRepository.deleteById(id);
    }

    /**
     * Réordonne (et reclasse) les PNJ d'un dossier : {@code order} = position, et le
     * dossier de chaque PNJ est posé à {@code folder} (déplacement par glisser-déposer).
     */
    @org.springframework.transaction.annotation.Transactional
    public void reorderNpcs(String folder, List<String> orderedIds) {
        String f = normalizeFolder(folder);
        ReorderSupport.reorder(orderedIds,
                npcRepository::findById,
                (npc, i) -> { npc.setFolder(f); npc.setOrder(i); },
                npcRepository::save);
    }

    public List<Npc> searchNpcs(String query) {
        if (query == null || query.isBlank()) return List.of();
        return npcRepository.searchByName(query.trim());
    }

    /** Trim le dossier ; chaîne vide → null (= non classé). */
    private static String normalizeFolder(String folder) {
        if (folder == null) return null;
        String trimmed = folder.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int nextOrderFor(String campaignId) {
        return npcRepository.findByCampaignId(campaignId).stream()
                .mapToInt(Npc::getOrder)
                .max()
                .orElse(-1) + 1;
    }
}
