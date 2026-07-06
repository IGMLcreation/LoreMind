package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.campaigncontext.quest.Prerequisite;
import com.loremind.infrastructure.web.dto.campaigncontext.PrerequisiteDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * Mapper entre Prerequisite (sealed type domaine) et PrerequisiteDTO (DTO API à discriminant).
 */
@Component
public class PrerequisiteMapper {

    public static final String KIND_QUEST_COMPLETED = "QUEST_COMPLETED";
    public static final String KIND_SESSION_REACHED = "SESSION_REACHED";
    public static final String KIND_FLAG_SET = "FLAG_SET";

    public PrerequisiteDTO toDTO(Prerequisite p) {
        if (p == null) return null;
        if (p instanceof Prerequisite.QuestCompleted q) {
            return new PrerequisiteDTO(KIND_QUEST_COMPLETED, q.questId(), null, null);
        }
        if (p instanceof Prerequisite.SessionReached s) {
            return new PrerequisiteDTO(KIND_SESSION_REACHED, null, s.minSessionNumber(), null);
        }
        if (p instanceof Prerequisite.FlagSet f) {
            return new PrerequisiteDTO(KIND_FLAG_SET, null, null, f.flagName());
        }
        throw new IllegalStateException("Prerequisite non géré : " + p.getClass().getName());
    }

    public Prerequisite toDomain(PrerequisiteDTO dto) {
        if (dto == null || dto.getKind() == null) return null;
        switch (dto.getKind()) {
            case KIND_QUEST_COMPLETED:
                return new Prerequisite.QuestCompleted(dto.getQuestId());
            case KIND_SESSION_REACHED:
                if (dto.getMinSessionNumber() == null) {
                    throw new IllegalArgumentException("minSessionNumber requis pour SESSION_REACHED");
                }
                return new Prerequisite.SessionReached(dto.getMinSessionNumber());
            case KIND_FLAG_SET:
                return new Prerequisite.FlagSet(dto.getFlagName());
            default:
                throw new IllegalArgumentException("Kind Prerequisite inconnu : " + dto.getKind());
        }
    }

    public List<PrerequisiteDTO> toDTOList(List<Prerequisite> list) {
        if (list == null) return new ArrayList<>();
        List<PrerequisiteDTO> out = new ArrayList<>(list.size());
        for (Prerequisite p : list) out.add(toDTO(p));
        return out;
    }

    public List<Prerequisite> toDomainList(List<PrerequisiteDTO> list) {
        if (list == null) return new ArrayList<>();
        List<Prerequisite> out = new ArrayList<>(list.size());
        for (PrerequisiteDTO d : list) out.add(toDomain(d));
        return out;
    }
}
