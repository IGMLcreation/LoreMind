package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Npc;
import com.loremind.domain.campaigncontext.ports.NpcRepository;
import org.springframework.stereotype.Service;

import java.util.List;
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

    /**
     * Parameter Object pour la création / mise à jour d'un Npc.
     * `order` est fourni par le controller ; si absent, le service le calcule.
     */
    public record NpcData(String name, String markdownContent, String campaignId, Integer order) {}

    public Npc createNpc(NpcData data) {
        int order = data.order() != null
                ? data.order()
                : nextOrderFor(data.campaignId());
        Npc npc = Npc.builder()
                .name(data.name())
                .markdownContent(data.markdownContent())
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
        existing.setMarkdownContent(data.markdownContent());
        if (data.order() != null) {
            existing.setOrder(data.order());
        }
        return npcRepository.save(existing);
    }

    public void deleteNpc(String id) {
        npcRepository.deleteById(id);
    }

    /** Renvoie la prochaine position libre — append en fin de liste. */
    private int nextOrderFor(String campaignId) {
        return npcRepository.findByCampaignId(campaignId).stream()
                .mapToInt(Npc::getOrder)
                .max()
                .orElse(-1) + 1;
    }
}
