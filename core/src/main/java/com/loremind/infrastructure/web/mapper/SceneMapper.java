package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.campaigncontext.Scene;
import com.loremind.domain.campaigncontext.SceneBranch;
import com.loremind.infrastructure.web.dto.campaigncontext.SceneBranchDTO;
import com.loremind.infrastructure.web.dto.campaigncontext.SceneDTO;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

/**
 * Mapper pour convertir entre Scene (entité de domaine) et SceneDTO.
 */
@Component
public class SceneMapper {

    public SceneDTO toDTO(Scene scene) {
        if (scene == null) {
            return null;
        }
        
        SceneDTO dto = new SceneDTO();
        dto.setId(scene.getId());
        dto.setName(scene.getName());
        dto.setDescription(scene.getDescription());
        dto.setChapterId(scene.getChapterId());
        dto.setOrder(scene.getOrder());
        dto.setIcon(scene.getIcon());
        dto.setLocation(scene.getLocation());
        dto.setTiming(scene.getTiming());
        dto.setAtmosphere(scene.getAtmosphere());
        dto.setPlayerNarration(scene.getPlayerNarration());
        dto.setGmSecretNotes(scene.getGmSecretNotes());
        dto.setChoicesConsequences(scene.getChoicesConsequences());
        dto.setCombatDifficulty(scene.getCombatDifficulty());
        dto.setEnemies(scene.getEnemies());
        dto.setRelatedPageIds(scene.getRelatedPageIds() != null
                ? new ArrayList<>(scene.getRelatedPageIds())
                : new ArrayList<>());
        dto.setIllustrationImageIds(scene.getIllustrationImageIds() != null
                ? new ArrayList<>(scene.getIllustrationImageIds())
                : new ArrayList<>());
        dto.setMapImageIds(scene.getMapImageIds() != null
                ? new ArrayList<>(scene.getMapImageIds())
                : new ArrayList<>());
        dto.setBranches(toBranchDTOs(scene.getBranches()));
        return dto;
    }

    public Scene toDomain(SceneDTO dto) {
        if (dto == null) {
            return null;
        }
        
        return Scene.builder()
                .id(dto.getId())
                .name(dto.getName())
                .description(dto.getDescription())
                .chapterId(dto.getChapterId())
                .order(dto.getOrder())
                .icon(dto.getIcon())
                .location(dto.getLocation())
                .timing(dto.getTiming())
                .atmosphere(dto.getAtmosphere())
                .playerNarration(dto.getPlayerNarration())
                .gmSecretNotes(dto.getGmSecretNotes())
                .choicesConsequences(dto.getChoicesConsequences())
                .combatDifficulty(dto.getCombatDifficulty())
                .enemies(dto.getEnemies())
                .relatedPageIds(dto.getRelatedPageIds() != null
                        ? new ArrayList<>(dto.getRelatedPageIds())
                        : new ArrayList<>())
                .illustrationImageIds(dto.getIllustrationImageIds() != null
                        ? new ArrayList<>(dto.getIllustrationImageIds())
                        : new ArrayList<>())
                .mapImageIds(dto.getMapImageIds() != null
                        ? new ArrayList<>(dto.getMapImageIds())
                        : new ArrayList<>())
                .branches(toBranchDomain(dto.getBranches()))
                .build();
    }

    // ─────────────── Mapping des branches (VO <-> DTO) ───────────────

    private List<SceneBranchDTO> toBranchDTOs(List<SceneBranch> branches) {
        if (branches == null) return new ArrayList<>();
        return branches.stream()
                .map(b -> new SceneBranchDTO(b.getLabel(), b.getTargetSceneId(), b.getCondition()))
                .collect(Collectors.toList());
    }

    private List<SceneBranch> toBranchDomain(List<SceneBranchDTO> dtos) {
        if (dtos == null) return new ArrayList<>();
        return dtos.stream()
                .map(d -> SceneBranch.builder()
                        .label(d.getLabel())
                        .targetSceneId(d.getTargetSceneId())
                        .condition(d.getCondition())
                        .build())
                .collect(Collectors.toList());
    }
}
