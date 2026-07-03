package com.loremind.infrastructure.transfer.dto;

import com.loremind.domain.campaigncontext.Room;
import com.loremind.domain.campaigncontext.SceneBattlemap;
import com.loremind.domain.campaigncontext.SceneBranch;
import com.loremind.domain.campaigncontext.SceneType;
import com.loremind.domain.shared.template.TemplateField;

import java.util.List;
import java.util.Map;

/**
 * Format d'export LOGIQUE (JSON) du "contenu" de LoreMind.
 * <p>
 * Records plats, miroirs des champs des entites JPA — volontairement DECOUPLES
 * des entites pour la stabilite inter-versions (le schema JPA peut bouger sans
 * casser un .zip exporte). Chaque record porte son {@code id} d'origine (Long)
 * afin que l'import puisse construire les maps de remapping oldId -> newId.
 * <p>
 * Les sous-objets riches (TemplateField, Prerequisite, SceneBranch, Room)
 * reutilisent les types domaine existants : Jackson sait les (de)serialiser
 * (records ou Lombok), et on evite de dupliquer la structure.
 */
public record ContentExport(
        Manifest manifest,
        List<GameSystemDto> gameSystems,
        List<LoreDto> lores,
        List<LoreNodeDto> loreNodes,
        List<TemplateDto> templates,
        List<PageDto> pages,
        List<CampaignDto> campaigns,
        List<ArcDto> arcs,
        List<ChapterDto> chapters,
        List<SceneDto> scenes,
        List<CharacterDto> characters,
        List<NpcDto> npcs,
        List<EnemyDto> enemies,
        List<ItemCatalogDto> itemCatalogs,
        List<RandomTableDto> randomTables,
        List<ImageDto> images,
        List<StoredFileDto> storedFiles,
        // --- Espace de jeu (format v2). Absent des exports v1 -> listes null a la
        //     relecture (Jackson) -> traitees comme vides cote import (nullSafe). ---
        List<PlaythroughDto> playthroughs,
        List<SessionDto> sessions,
        List<SessionEntryDto> sessionEntries,
        List<PlaythroughFlagDto> playthroughFlags,
        List<QuestProgressionDto> questProgressions,
        // --- Quêtes (Niveau 1). Absent des bundles antérieurs -> null à la relecture (nullSafe). ---
        List<QuestDto> quests,
        // --- Horloges de progression (Clocks, état de Partie). Absent des anciens bundles -> nullSafe. ---
        List<ClockDto> clocks,
        // --- Fronts (menaces regroupant des horloges). Absent des anciens bundles -> nullSafe. ---
        List<FrontDto> fronts
) {

    /**
     * Metadonnees de l'export. {@code exportedAt} est passe en parametre depuis
     * la couche requete (PAS Instant.now() ici, pour rester deterministe et
     * testable). {@code scope} decrit le perimetre ("complète" ou nom de campagne)
     * a titre informatif.
     */
    public record Manifest(
            int formatVersion,
            String appVersion,
            String exportedAt,
            String scope
    ) {}

    public record GameSystemDto(
            Long id,
            String name,
            String description,
            String rulesMarkdown,
            List<TemplateField> characterTemplate,
            List<TemplateField> npcTemplate,
            List<TemplateField> enemyTemplate,
            String foundryActorType,
            String author,
            boolean isPublic
    ) {}

    public record LoreDto(
            Long id,
            String name,
            String description,
            int nodeCount,
            int pageCount
    ) {}

    public record LoreNodeDto(
            Long id,
            String name,
            String icon,
            Long parentId,
            Long loreId
    ) {}

    public record TemplateDto(
            Long id,
            Long loreId,
            String name,
            String description,
            Long defaultNodeId,
            List<TemplateField> fields
    ) {}

    public record PageDto(
            Long id,
            Long loreId,
            Long nodeId,
            Long templateId,
            String title,
            Map<String, String> values,
            Map<String, List<String>> imageValues,
            Map<String, Map<String, com.loremind.domain.lorecontext.ImageFraming>> imageFraming,
            Map<String, Map<String, String>> keyValueValues,
            Map<String, List<Map<String, String>>> tableValues,
            String notes,
            List<String> tags,
            List<String> relatedPageIds
    ) {}

    public record CampaignDto(
            Long id,
            String name,
            String description,
            int arcsCount,
            int playerCount,
            String loreId,
            String gameSystemId
    ) {}

    public record ArcDto(
            Long id,
            String name,
            String description,
            Long campaignId,
            int order,
            String type,
            String icon,
            String themes,
            String stakes,
            String gmNotes,
            String rewards,
            String resolution,
            List<String> relatedPageIds,
            List<String> illustrationImageIds
    ) {}

    public record ChapterDto(
            Long id,
            String name,
            String description,
            Long arcId,
            int order,
            // Prerequisites en JSON brut (format "kind" du converter JPA) : Prerequisite
            // est un type scellé SANS annotations Jackson, donc impossible à (dé)sérialiser
            // en polymorphe via l'ObjectMapper standard. On réutilise
            // PrerequisiteListJsonConverter (format on-disk stable, identique à la base).
            String prerequisitesJson,
            String icon,
            String gmNotes,
            String playerObjectives,
            String narrativeStakes,
            List<String> relatedPageIds,
            List<String> illustrationImageIds
    ) {}

    public record SceneDto(
            Long id,
            String name,
            String description,
            Long chapterId,
            int order,
            String icon,
            String location,
            String timing,
            String atmosphere,
            String playerNarration,
            String gmSecretNotes,
            String choicesConsequences,
            String combatDifficulty,
            String enemies,
            List<String> enemyIds,
            List<String> relatedPageIds,
            List<String> illustrationImageIds,
            // LEGACY (exports antérieurs à V22) : paire unique, lue à l'import pour
            // reconstituer une entrée de `battlemaps`. Toujours null sur les nouveaux exports.
            String battlemapMediaFileId,
            String battlemapDataFileId,
            // Battlemaps multiples (variantes Jour/Nuit, étages…) — remplace la paire ci-dessus.
            List<SceneBattlemap> battlemaps,
            List<SceneBranch> branches,
            List<Room> rooms,
            SceneType type,
            Double graphX,
            Double graphY
    ) {}

    public record CharacterDto(
            Long id,
            String name,
            String portraitImageId,
            String headerImageId,
            Map<String, String> values,
            Map<String, List<String>> imageValues,
            Map<String, Map<String, String>> keyValueValues,
            Long campaignId,
            Long playthroughId,
            int order
    ) {}

    public record NpcDto(
            Long id,
            String name,
            String portraitImageId,
            String headerImageId,
            Map<String, String> values,
            Map<String, List<String>> imageValues,
            Map<String, Map<String, String>> keyValueValues,
            Long campaignId,
            List<String> relatedPageIds,
            String folder,
            int order
    ) {}

    public record EnemyDto(
            Long id,
            String name,
            String level,
            String folder,
            String portraitImageId,
            String headerImageId,
            Map<String, String> values,
            Map<String, List<String>> imageValues,
            Map<String, Map<String, String>> keyValueValues,
            Long campaignId,
            String foundryRef,
            Map<String, String> foundryStats,
            int order
    ) {}

    public record ItemCatalogDto(
            Long id,
            String name,
            String description,
            String icon,
            Long campaignId,
            int order,
            List<CatalogItemDto> items
    ) {}

    public record CatalogItemDto(
            Long id,
            String name,
            String price,
            String category,
            String description,
            int position
    ) {}

    public record RandomTableDto(
            Long id,
            String name,
            String description,
            String diceFormula,
            String icon,
            Long campaignId,
            int order,
            List<RandomTableEntryDto> entries
    ) {}

    public record RandomTableEntryDto(
            Long id,
            int minRoll,
            int maxRoll,
            String label,
            String detail,
            int position
    ) {}

    /**
     * Metadonnees d'une image. Le binaire voyage a part dans le zip sous
     * {@code images/<storageKey>}. La cle est PRESERVEE telle quelle a l'import.
     */
    public record ImageDto(
            Long id,
            String filename,
            String contentType,
            long sizeBytes,
            String storageKey
    ) {}

    /**
     * Metadonnees d'un fichier generique (battlemap : media + sidecar JSON).
     * Le binaire voyage a part dans le zip sous {@code files/<storageKey>}.
     * La cle est PRESERVEE telle quelle a l'import (meme logique que ImageDto).
     */
    public record StoredFileDto(
            Long id,
            String filename,
            String contentType,
            long sizeBytes,
            String storageKey
    ) {}

    // ===================================================================== Jeu (v2)

    /** Une Partie (Playthrough) : un run d'une campagne par un groupe. */
    public record PlaythroughDto(
            Long id,
            Long campaignId,
            String name,
            String description
    ) {}

    /** Une séance de jeu rattachée à une Partie. Horodatages en ISO-8601 (ou null). */
    public record SessionDto(
            Long id,
            String name,
            String campaignId,
            Long playthroughId,
            String startedAt,
            String endedAt
    ) {}

    /** Une entrée du journal d'une séance ({@code type} = nom de l'EntryType). */
    public record SessionEntryDto(
            Long id,
            String sessionId,
            String type,
            String content,
            String occurredAt
    ) {}

    /** Un drapeau de Partie (flag narratif booléen). */
    public record PlaythroughFlagDto(
            Long id,
            Long playthroughId,
            String name,
            boolean value
    ) {}

    /** Une horloge de progression d'une Partie (Clock, façon Blades in the Dark). */
    public record ClockDto(
            Long id,
            Long playthroughId,
            String name,
            String description,
            int segments,
            int filled,
            int order,
            com.loremind.domain.playcontext.ClockTrigger triggerType,
            String triggerRef,
            Long frontId
    ) {}

    /** Un Front (menace regroupant des horloges) d'une Partie. */
    public record FrontDto(
            Long id,
            Long playthroughId,
            String name,
            String description,
            int order
    ) {}

    /**
     * La progression d'une quête dans une Partie ({@code status} = nom du ProgressionStatus).
     * NB : le champ {@code chapterId} porte désormais le quest id (== chapter id partagé en
     * format v1) — nom conservé pour la rétrocompat de lecture des anciens bundles.
     */
    public record QuestProgressionDto(
            Long id,
            Long playthroughId,
            Long chapterId,
            String status
    ) {}

    /**
     * Une Quête (Niveau 1) : entité orthogonale rattachée à la campagne.
     * {@code prerequisitesJson} / {@code nodesJson} = JSON brut des converters JPA
     * (même logique que {@code ChapterDto.prerequisitesJson}).
     */
    public record QuestDto(
            Long id,
            Long campaignId,
            Long arcId,
            String name,
            String description,
            String icon,
            int order,
            String prerequisitesJson,
            String nodesJson,
            String gmNotes,
            String playerObjectives,
            String narrativeStakes,
            List<String> relatedPageIds,
            List<String> illustrationImageIds
    ) {}
}
