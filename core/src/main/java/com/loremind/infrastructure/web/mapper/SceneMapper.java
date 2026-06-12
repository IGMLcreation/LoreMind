package com.loremind.infrastructure.web.mapper;

import com.loremind.domain.campaigncontext.Room;
import com.loremind.domain.campaigncontext.RoomBranch;
import com.loremind.domain.campaigncontext.Scene;
import com.loremind.domain.campaigncontext.SceneBranch;
import com.loremind.infrastructure.web.dto.campaigncontext.RoomBranchDTO;
import com.loremind.infrastructure.web.dto.campaigncontext.RoomDTO;
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
        dto.setEnemyIds(scene.getEnemyIds() != null
                ? new ArrayList<>(scene.getEnemyIds())
                : new ArrayList<>());
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
        dto.setRooms(toRoomDTOs(scene.getRooms()));
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
                .enemyIds(dto.getEnemyIds() != null
                        ? new ArrayList<>(dto.getEnemyIds())
                        : new ArrayList<>())
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
                .rooms(toRoomDomain(dto.getRooms()))
                .build();
    }

    // ─────────────── Mapping des branches (VO <-> DTO) ───────────────

    private List<SceneBranchDTO> toBranchDTOs(List<SceneBranch> branches) {
        if (branches == null) return new ArrayList<>();
        return branches.stream()
                .map(b -> new SceneBranchDTO(b.label(), b.targetSceneId(), b.condition()))
                .collect(Collectors.toList());
    }

    private List<SceneBranch> toBranchDomain(List<SceneBranchDTO> dtos) {
        if (dtos == null) return new ArrayList<>();
        return dtos.stream()
                .map(d -> new SceneBranch(d.getLabel(), d.getTargetSceneId(), d.getCondition()))
                .collect(Collectors.toList());
    }

    // ─────────────── Mapping des pièces (VO <-> DTO) ───────────────

    private List<RoomDTO> toRoomDTOs(List<Room> rooms) {
        if (rooms == null) return new ArrayList<>();
        return rooms.stream().map(this::toRoomDTO).collect(Collectors.toList());
    }

    private RoomDTO toRoomDTO(Room r) {
        RoomDTO dto = new RoomDTO();
        dto.setId(r.getId());
        dto.setName(r.getName());
        dto.setDescription(r.getDescription());
        dto.setEnemies(r.getEnemies());
        dto.setEnemyIds(r.getEnemyIds() != null
                ? new ArrayList<>(r.getEnemyIds())
                : new ArrayList<>());
        dto.setLoot(r.getLoot());
        dto.setTraps(r.getTraps());
        dto.setGmNotes(r.getGmNotes());
        dto.setFloor(r.getFloor());
        dto.setOrder(r.getOrder());
        dto.setIllustrationImageIds(r.getIllustrationImageIds() != null
                ? new ArrayList<>(r.getIllustrationImageIds())
                : new ArrayList<>());
        dto.setMapImageId(r.getMapImageId());
        dto.setBranches(r.getBranches() == null
                ? new ArrayList<>()
                : r.getBranches().stream()
                    .map(b -> new RoomBranchDTO(b.label(), b.targetRoomId(), b.condition()))
                    .collect(Collectors.toList()));
        return dto;
    }

    private List<Room> toRoomDomain(List<RoomDTO> dtos) {
        if (dtos == null) return new ArrayList<>();
        return dtos.stream().map(this::toRoomDomain).collect(Collectors.toList());
    }

    private Room toRoomDomain(RoomDTO d) {
        return Room.builder()
                .id(d.getId())
                .name(d.getName())
                .description(d.getDescription())
                .enemies(d.getEnemies())
                .enemyIds(d.getEnemyIds() != null
                        ? new ArrayList<>(d.getEnemyIds())
                        : new ArrayList<>())
                .loot(d.getLoot())
                .traps(d.getTraps())
                .gmNotes(d.getGmNotes())
                .floor(d.getFloor())
                .order(d.getOrder())
                .illustrationImageIds(d.getIllustrationImageIds() != null
                        ? new ArrayList<>(d.getIllustrationImageIds())
                        : new ArrayList<>())
                .mapImageId(d.getMapImageId())
                .branches(d.getBranches() == null
                        ? new ArrayList<>()
                        : d.getBranches().stream()
                            .map(b -> new RoomBranch(b.getLabel(), b.getTargetRoomId(), b.getCondition()))
                            .collect(Collectors.toList()))
                .build();
    }
}
