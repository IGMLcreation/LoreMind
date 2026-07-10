package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.QuestService;
import com.loremind.application.campaigncontext.QuestStatusEnricher;
import com.loremind.domain.campaigncontext.quest.Quest;
import com.loremind.infrastructure.web.dto.campaigncontext.QuestDTO;
import com.loremind.infrastructure.web.mapper.QuestMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * REST Controller pour le contexte Quest (Niveau 1). Quêtes ORTHOGONALES à l'arbre,
 * rattachées à la campagne (décision D2).
 *
 * <p>Si {@code ?playthroughId=} est fourni, les DTOs sont enrichis de leur
 * {@code progressionStatus} et {@code effectiveStatus} relatifs à ce Playthrough.</p>
 */
@RestController
@RequestMapping("/api/campaigns/{campaignId}/quests")
public class QuestController {

    private final QuestService questService;
    private final QuestMapper questMapper;
    private final QuestStatusEnricher statusEnricher;

    public QuestController(QuestService questService,
                           QuestMapper questMapper,
                           QuestStatusEnricher statusEnricher) {
        this.questService = questService;
        this.questMapper = questMapper;
        this.statusEnricher = statusEnricher;
    }

    @PostMapping
    public ResponseEntity<QuestDTO> createQuest(@PathVariable String campaignId,
                                                @RequestBody QuestDTO questDTO) {
        Quest domain = questMapper.toDomain(questDTO);
        domain.setCampaignId(campaignId); // le path fait autorité
        Quest created = questService.createQuest(domain);
        return ResponseEntity.ok(questMapper.toDTO(created));
    }

    @GetMapping
    public ResponseEntity<List<QuestDTO>> listByCampaign(
            @PathVariable String campaignId,
            @RequestParam(value = "playthroughId", required = false) String playthroughId) {
        List<Quest> quests = questService.getQuestsByCampaignId(campaignId);
        List<QuestDTO> dtos = quests.stream().map(questMapper::toDTO).toList();
        if (playthroughId != null && !playthroughId.isBlank()) {
            statusEnricher.enrich(dtos, quests, playthroughId);
        }
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/{questId}")
    public ResponseEntity<QuestDTO> getQuestById(
            @PathVariable String campaignId,
            @PathVariable String questId,
            @RequestParam(value = "playthroughId", required = false) String playthroughId) {
        return questService.getQuestById(questId)
                .map(quest -> {
                    QuestDTO dto = questMapper.toDTO(quest);
                    if (playthroughId != null && !playthroughId.isBlank()) {
                        statusEnricher.enrich(List.of(dto), List.of(quest), playthroughId);
                    }
                    return ResponseEntity.ok(dto);
                })
                .orElse(ResponseEntity.notFound().build());
    }

    @PutMapping("/{questId}")
    public ResponseEntity<QuestDTO> updateQuest(@PathVariable String campaignId,
                                                @PathVariable String questId,
                                                @RequestBody QuestDTO questDTO) {
        Quest domain = questMapper.toDomain(questDTO);
        domain.setCampaignId(campaignId);
        Quest updated = questService.updateQuest(questId, domain);
        return ResponseEntity.ok(questMapper.toDTO(updated));
    }

    @DeleteMapping("/{questId}")
    public ResponseEntity<Void> deleteQuest(@PathVariable String campaignId, @PathVariable String questId) {
        questService.deleteQuest(questId);
        return ResponseEntity.noContent().build();
    }

    /** Impact d'une suppression : scènes du conteneur (quête libre) qui partiront avec. */
    @GetMapping("/{questId}/deletion-impact")
    public ResponseEntity<QuestService.DeletionImpact> getDeletionImpact(@PathVariable String campaignId,
                                                                         @PathVariable String questId) {
        if (questService.getQuestById(questId).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(questService.getDeletionImpact(questId));
    }

    /** Réordonne les quêtes de la campagne : order = position. */
    @PutMapping("/reorder")
    public ResponseEntity<Void> reorder(@PathVariable String campaignId, @RequestBody ReorderRequest req) {
        questService.reorderQuests(campaignId, req.orderedIds());
        return ResponseEntity.noContent().build();
    }

    public record ReorderRequest(List<String> orderedIds) {}
}
