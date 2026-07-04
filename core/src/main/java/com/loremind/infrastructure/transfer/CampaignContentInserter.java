package com.loremind.infrastructure.transfer;

import com.loremind.domain.campaigncontext.SceneBattlemap;
import com.loremind.domain.campaigncontext.SceneType;
import com.loremind.infrastructure.persistence.converter.PrerequisiteListJsonConverter;
import com.loremind.infrastructure.persistence.converter.QuestNodeListJsonConverter;
import com.loremind.infrastructure.persistence.entity.ArcJpaEntity;
import com.loremind.infrastructure.persistence.entity.CampaignJpaEntity;
import com.loremind.infrastructure.persistence.entity.CatalogItemJpaEntity;
import com.loremind.infrastructure.persistence.entity.ChapterJpaEntity;
import com.loremind.infrastructure.persistence.entity.EnemyJpaEntity;
import com.loremind.infrastructure.persistence.entity.ItemCatalogJpaEntity;
import com.loremind.infrastructure.persistence.entity.NpcJpaEntity;
import com.loremind.infrastructure.persistence.entity.QuestJpaEntity;
import com.loremind.infrastructure.persistence.entity.RandomTableEntryJpaEntity;
import com.loremind.infrastructure.persistence.entity.RandomTableJpaEntity;
import com.loremind.infrastructure.persistence.entity.SceneJpaEntity;
import com.loremind.infrastructure.persistence.jpa.ArcJpaRepository;
import com.loremind.infrastructure.persistence.jpa.CampaignJpaRepository;
import com.loremind.infrastructure.persistence.jpa.ChapterJpaRepository;
import com.loremind.infrastructure.persistence.jpa.EnemyJpaRepository;
import com.loremind.infrastructure.persistence.jpa.ItemCatalogJpaRepository;
import com.loremind.infrastructure.persistence.jpa.NpcJpaRepository;
import com.loremind.infrastructure.persistence.jpa.QuestJpaRepository;
import com.loremind.infrastructure.persistence.jpa.RandomTableJpaRepository;
import com.loremind.infrastructure.persistence.jpa.SceneJpaRepository;
import com.loremind.infrastructure.transfer.dto.ContentExport;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 1re passe d'import du contenu de campagne (cf. {@link ImportService}) : Campaign,
 * Arc, ItemCatalog, RandomTable, Chapter, Quest (v2 ou conversion legacy via
 * {@link LegacyQuestConverter}), Npc, Enemy, Scene. Alimente les maps de remapping
 * correspondantes de {@link ImportIdMaps}.
 */
@Component
class CampaignContentInserter {

    // (Dé)sérialise les prérequis dans le format "kind" du converter JPA (Prerequisite
    // est scellé, non sérialisable en polymorphe par l'ObjectMapper standard).
    private static final PrerequisiteListJsonConverter PREREQ_CONVERTER = new PrerequisiteListJsonConverter();
    private static final QuestNodeListJsonConverter NODE_CONVERTER = new QuestNodeListJsonConverter();

    private final CampaignJpaRepository campaignRepo;
    private final ArcJpaRepository arcRepo;
    private final ItemCatalogJpaRepository itemCatalogRepo;
    private final RandomTableJpaRepository randomTableRepo;
    private final ChapterJpaRepository chapterRepo;
    private final QuestJpaRepository questRepo;
    private final NpcJpaRepository npcRepo;
    private final EnemyJpaRepository enemyRepo;
    private final SceneJpaRepository sceneRepo;
    private final LegacyQuestConverter legacyQuestConverter;

    CampaignContentInserter(CampaignJpaRepository campaignRepo,
                            ArcJpaRepository arcRepo,
                            ItemCatalogJpaRepository itemCatalogRepo,
                            RandomTableJpaRepository randomTableRepo,
                            ChapterJpaRepository chapterRepo,
                            QuestJpaRepository questRepo,
                            NpcJpaRepository npcRepo,
                            EnemyJpaRepository enemyRepo,
                            SceneJpaRepository sceneRepo,
                            LegacyQuestConverter legacyQuestConverter) {
        this.campaignRepo = campaignRepo;
        this.arcRepo = arcRepo;
        this.itemCatalogRepo = itemCatalogRepo;
        this.randomTableRepo = randomTableRepo;
        this.chapterRepo = chapterRepo;
        this.questRepo = questRepo;
        this.npcRepo = npcRepo;
        this.enemyRepo = enemyRepo;
        this.sceneRepo = sceneRepo;
        this.legacyQuestConverter = legacyQuestConverter;
    }

    void insert(ContentExport export, ImportIdMaps maps, ImportResult.Builder result) {
        // -- Campaign (loreId/gameSystemId String remappes en 2e passe)
        for (ContentExport.CampaignDto d : nullSafe(export.campaigns())) {
            CampaignJpaEntity e = new CampaignJpaEntity();
            e.setName(d.name());
            e.setDescription(d.description());
            e.setArcsCount(d.arcsCount());
            e.setPlayerCount(d.playerCount());
            e.setLoreId(d.loreId());             // remappe en 2e passe
            e.setGameSystemId(d.gameSystemId()); // remappe en 2e passe
            maps.campaignMap.put(d.id(), campaignRepo.save(e).getId());
        }
        result.count("campaigns", maps.campaignMap.size());

        // -- Arc (relatedPageIds remappe en 2e passe)
        for (ContentExport.ArcDto d : nullSafe(export.arcs())) {
            ArcJpaEntity e = new ArcJpaEntity();
            e.setName(d.name());
            e.setDescription(d.description());
            e.setCampaignId(IdRemapper.remapId(maps.campaignMap, d.campaignId()));
            e.setOrder(d.order());
            e.setType(IdRemapper.parseArcType(d.type()));
            e.setIcon(d.icon());
            e.setThemes(d.themes());
            e.setStakes(d.stakes());
            e.setGmNotes(d.gmNotes());
            e.setRewards(d.rewards());
            e.setResolution(d.resolution());
            e.setRelatedPageIds(d.relatedPageIds()); // remappe en 2e passe
            e.setIllustrationImageIds(d.illustrationImageIds());
            maps.arcMap.put(d.id(), arcRepo.save(e).getId());
        }
        result.count("arcs", maps.arcMap.size());

        // -- ItemCatalog (+ items en cascade)
        int catalogCount = 0, itemCount = 0;
        for (ContentExport.ItemCatalogDto d : nullSafe(export.itemCatalogs())) {
            ItemCatalogJpaEntity e = new ItemCatalogJpaEntity();
            e.setName(d.name());
            e.setDescription(d.description());
            e.setIcon(d.icon());
            e.setCampaignId(IdRemapper.remapId(maps.campaignMap, d.campaignId()));
            e.setOrder(d.order());
            List<CatalogItemJpaEntity> items = new ArrayList<>();
            for (ContentExport.CatalogItemDto i : nullSafe(d.items())) {
                CatalogItemJpaEntity item = new CatalogItemJpaEntity();
                item.setName(i.name());
                item.setPrice(i.price());
                item.setCategory(i.category());
                item.setDescription(i.description());
                item.setPosition(i.position());
                item.setCatalog(e); // lien parent requis pour la cascade
                items.add(item);
                itemCount++;
            }
            e.setItems(items);
            itemCatalogRepo.save(e);
            catalogCount++;
        }
        result.count("itemCatalogs", catalogCount);
        result.count("catalogItems", itemCount);

        // -- RandomTable (+ entries en cascade)
        int tableCount = 0, entryCount = 0;
        for (ContentExport.RandomTableDto d : nullSafe(export.randomTables())) {
            RandomTableJpaEntity e = new RandomTableJpaEntity();
            e.setName(d.name());
            e.setDescription(d.description());
            e.setDiceFormula(d.diceFormula());
            e.setIcon(d.icon());
            e.setCampaignId(IdRemapper.remapId(maps.campaignMap, d.campaignId()));
            e.setOrder(d.order());
            List<RandomTableEntryJpaEntity> entries = new ArrayList<>();
            for (ContentExport.RandomTableEntryDto en : nullSafe(d.entries())) {
                RandomTableEntryJpaEntity entryE = new RandomTableEntryJpaEntity();
                entryE.setMinRoll(en.minRoll());
                entryE.setMaxRoll(en.maxRoll());
                entryE.setLabel(en.label());
                entryE.setDetail(en.detail());
                entryE.setPosition(en.position());
                entryE.setRandomTable(e);
                entries.add(entryE);
                entryCount++;
            }
            e.setEntries(entries);
            randomTableRepo.save(e);
            tableCount++;
        }
        result.count("randomTables", tableCount);
        result.count("randomTableEntries", entryCount);

        // -- Chapter (relatedPageIds remappes en 2e passe). Les chapitres n'ont plus de
        //    prérequis (Niveau 1) : d.prerequisitesJson() reste lu par la conversion legacy.
        for (ContentExport.ChapterDto d : nullSafe(export.chapters())) {
            ChapterJpaEntity e = new ChapterJpaEntity();
            e.setName(d.name());
            e.setDescription(d.description());
            e.setArcId(IdRemapper.remapId(maps.arcMap, d.arcId()));
            e.setOrder(d.order());
            e.setIcon(d.icon());
            e.setGmNotes(d.gmNotes());
            e.setPlayerObjectives(d.playerObjectives());
            e.setNarrativeStakes(d.narrativeStakes());
            e.setRelatedPageIds(d.relatedPageIds()); // remappe en 2e passe
            e.setIllustrationImageIds(d.illustrationImageIds());
            maps.chapterMap.put(d.id(), chapterRepo.save(e).getId());
        }
        result.count("chapters", maps.chapterMap.size());

        // -- Quest v2 (le bundle porte un champ quests) : campaignId remappé tout de suite ;
        //    prereqs / nodes / relatedPageIds remappés en 2e passe (sceneMap pas encore prêt).
        for (ContentExport.QuestDto d : nullSafe(export.quests())) {
            QuestJpaEntity e = new QuestJpaEntity();
            e.setCampaignId(IdRemapper.remapId(maps.campaignMap, d.campaignId()));
            e.setArcId(IdRemapper.remapId(maps.arcMap, d.arcId())); // arcMap déjà prêt (arcs importés avant) ; null→null
            e.setName(d.name());
            e.setDescription(d.description());
            e.setIcon(d.icon());
            e.setOrder(d.order());
            e.setPrerequisites(PREREQ_CONVERTER.convertToEntityAttribute(d.prerequisitesJson())); // remappé 2e passe
            e.setNodes(NODE_CONVERTER.convertToEntityAttribute(d.nodesJson()));                    // remappé 2e passe
            e.setGmNotes(d.gmNotes());
            e.setPlayerObjectives(d.playerObjectives());
            e.setNarrativeStakes(d.narrativeStakes());
            e.setRelatedPageIds(d.relatedPageIds());                                               // remappé 2e passe
            e.setIllustrationImageIds(d.illustrationImageIds());
            maps.questMap.put(d.id(), questRepo.save(e).getId());
        }

        // -- Quest legacy (bundle SANS champ quests) : conversion des chapitres qui jouaient
        //    le rôle de quête. Ici questMap est clé par ANCIEN CHAPTER id (pas de collision :
        //    on compare chapter-id à chapter-id).
        if (export.quests() == null) {
            legacyQuestConverter.convertLegacyChaptersToQuests(export, maps);
        }
        result.count("quests", maps.questMap.size());

        // -- Npc (relatedPageIds remappe en 2e passe)
        for (ContentExport.NpcDto d : nullSafe(export.npcs())) {
            NpcJpaEntity e = new NpcJpaEntity();
            e.setName(d.name());
            e.setPortraitImageId(d.portraitImageId());
            e.setHeaderImageId(d.headerImageId());
            e.setValues(d.values());
            e.setImageValues(d.imageValues());
            e.setKeyValueValues(d.keyValueValues());
            e.setCampaignId(IdRemapper.remapId(maps.campaignMap, d.campaignId()));
            e.setRelatedPageIds(d.relatedPageIds()); // remappe en 2e passe
            e.setFolder(d.folder());
            e.setOrder(d.order());
            maps.npcMap.put(d.id(), npcRepo.save(e).getId());
        }
        result.count("npcs", maps.npcMap.size());

        // -- Enemy
        for (ContentExport.EnemyDto d : nullSafe(export.enemies())) {
            EnemyJpaEntity e = new EnemyJpaEntity();
            e.setName(d.name());
            e.setLevel(d.level());
            e.setFolder(d.folder());
            e.setPortraitImageId(d.portraitImageId());
            e.setHeaderImageId(d.headerImageId());
            e.setValues(d.values());
            e.setImageValues(d.imageValues());
            e.setKeyValueValues(d.keyValueValues());
            e.setCampaignId(IdRemapper.remapId(maps.campaignMap, d.campaignId()));
            e.setFoundryRef(d.foundryRef()); // ref externe Foundry : conservee telle quelle
            e.setFoundryStats(d.foundryStats());
            e.setOrder(d.order());
            maps.enemyMap.put(d.id(), enemyRepo.save(e).getId());
        }
        result.count("enemies", maps.enemyMap.size());

        // -- Scene (enemyIds + relatedPageIds + branches remappes en 2e passe)
        for (ContentExport.SceneDto d : nullSafe(export.scenes())) {
            SceneJpaEntity e = new SceneJpaEntity();
            e.setName(d.name());
            e.setDescription(d.description());
            e.setChapterId(IdRemapper.remapId(maps.chapterMap, d.chapterId()));
            e.setOrder(d.order());
            e.setIcon(d.icon());
            e.setType(d.type() != null ? d.type() : SceneType.GENERIC);
            e.setLocation(d.location());
            e.setTiming(d.timing());
            e.setAtmosphere(d.atmosphere());
            e.setPlayerNarration(d.playerNarration());
            e.setGmSecretNotes(d.gmSecretNotes());
            e.setChoicesConsequences(d.choicesConsequences());
            e.setCombatDifficulty(d.combatDifficulty());
            e.setEnemies(d.enemies());
            e.setEnemyIds(d.enemyIds());            // remappe en 2e passe
            e.setRelatedPageIds(d.relatedPageIds()); // remappe en 2e passe
            e.setIllustrationImageIds(d.illustrationImageIds());
            // Battlemaps : ids StoredFile passes tels quels (meme logique que les refs
            // d'images illustration, non remappees). Les exports anterieurs a V22
            // portaient une paire unique -> reconstituee en premiere entree de liste.
            List<SceneBattlemap> battlemaps = d.battlemaps();
            if ((battlemaps == null || battlemaps.isEmpty())
                    && (d.battlemapMediaFileId() != null || d.battlemapDataFileId() != null)) {
                battlemaps = List.of(new SceneBattlemap("", d.battlemapMediaFileId(), d.battlemapDataFileId()));
            }
            e.setBattlemaps(battlemaps != null ? battlemaps : List.of());
            e.setGraphX(d.graphX());
            e.setGraphY(d.graphY());
            e.setBranches(d.branches());             // remappe en 2e passe
            e.setRooms(d.rooms());                   // Rooms: UUID, non remappes
            maps.sceneMap.put(d.id(), sceneRepo.save(e).getId());
        }
        result.count("scenes", maps.sceneMap.size());
    }

    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }
}
