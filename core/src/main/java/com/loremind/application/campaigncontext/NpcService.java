package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Npc;
import com.loremind.domain.campaigncontext.ports.NpcRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
            String campaignId,
            Integer order
    ) {}

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
                .campaignId(data.campaignId())
                .order(order)
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
        if (data.order() != null) {
            existing.setOrder(data.order());
        }
        return npcRepository.save(existing);
    }

    public void deleteNpc(String id) {
        npcRepository.deleteById(id);
    }

    private int nextOrderFor(String campaignId) {
        return npcRepository.findByCampaignId(campaignId).stream()
                .mapToInt(Npc::getOrder)
                .max()
                .orElse(-1) + 1;
    }
}
