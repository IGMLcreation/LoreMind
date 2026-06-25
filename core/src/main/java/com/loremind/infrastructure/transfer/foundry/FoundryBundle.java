package com.loremind.infrastructure.transfer.foundry;

import java.util.List;
import java.util.Map;

/**
 * Records du bundle d'export Foundry (cf. {@code docs/foundry-bundle-schema.md}).
 * <p>
 * Contrat NEUTRE vis-a-vis de Foundry : decrit fidelement les entites LoreMind ;
 * c'est le module Foundry {@code loremind-importer} qui decide du mapping vers
 * dossiers / scenes / journaux. Serialise avec inclusion NON_NULL (les champs
 * absents disparaissent du JSON).
 */
public final class FoundryBundle {

    private FoundryBundle() {}

    public record Manifest(
            String formatVersion,
            String generator,
            String appVersion,
            String exportedAt,
            String campaignId,
            String campaignName,
            String contentFormat,
            Map<String, Integer> counts
    ) {}

    /** Contenu de data.json : campagne + listes a plat + index des assets. */
    public record Data(
            String formatVersion,
            Campaign campaign,
            List<Arc> arcs,
            List<Quest> quests,
            List<Scene> scenes,
            List<Persona> npcs,
            List<Persona> enemies,
            List<Asset> assets
    ) {}

    public record Campaign(String id, String name, String description, String gameSystemId) {}

    public record Arc(
            String id, String name, String description, int order, String type, String icon,
            String themes, String stakes, String gmNotes, String rewards, String resolution,
            List<String> illustrationAssetIds
    ) {}

    public record Quest(
            String id, String arcId, String name, String description, int order, String icon,
            String playerObjectives, String narrativeStakes, String gmNotes,
            List<Map<String, Object>> prerequisites, List<String> illustrationAssetIds
    ) {}

    public record Scene(
            String id, String questId, String name, String description, int order, String icon,
            String location, String timing, String atmosphere,
            String playerNarration, String gmSecretNotes, String choicesConsequences,
            String combatDifficulty, String enemies, List<String> enemyIds,
            List<String> illustrationAssetIds, Battlemap battlemap,
            List<Branch> branches, List<Room> rooms
    ) {}

    public record Battlemap(String mediaAssetId, String dataAssetId) {}

    public record Branch(String label, String targetSceneId, String condition) {}

    public record Room(
            String id, String name, String description, String enemies, List<String> enemyIds,
            String loot, String traps, String gmNotes, Integer floor, int order,
            List<String> illustrationAssetIds, Battlemap battlemap, List<RoomBranch> branches
    ) {}

    public record RoomBranch(String label, String targetRoomId, String condition) {}

    public record Persona(
            String id, String name, String folder, int order,
            String portraitAssetId, String headerAssetId, String level, List<Field> fields
    ) {}

    /** Champ de fiche resolu : {type, label} + selon le type value | entries | assetIds. */
    public record Field(String type, String label, String value, List<Entry> entries, List<String> assetIds) {}

    public record Entry(String label, String value) {}

    public record Asset(String id, String kind, String path, String filename, String mime, long sizeBytes) {}
}
