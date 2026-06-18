package com.loremind.infrastructure.transfer.dto;

import com.loremind.domain.campaigncontext.Room;
import com.loremind.domain.campaigncontext.SceneBranch;
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
        List<ImageDto> images
) {

    /**
     * Metadonnees de l'export. {@code exportedAt} est passe en parametre depuis
     * la couche requete (PAS Instant.now() ici, pour rester deterministe et
     * testable).
     */
    public record Manifest(
            int formatVersion,
            String appVersion,
            String exportedAt
    ) {}

    public record GameSystemDto(
            Long id,
            String name,
            String description,
            String rulesMarkdown,
            List<TemplateField> characterTemplate,
            List<TemplateField> npcTemplate,
            List<TemplateField> enemyTemplate,
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
            List<String> illustrationImageIds,
            List<String> mapImageIds
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
            List<String> illustrationImageIds,
            List<String> mapImageIds
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
            List<String> mapImageIds,
            List<SceneBranch> branches,
            List<Room> rooms
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
}
