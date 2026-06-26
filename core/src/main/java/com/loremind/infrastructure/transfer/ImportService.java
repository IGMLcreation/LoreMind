package com.loremind.infrastructure.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.ProgressionStatus;
import com.loremind.domain.playcontext.EntryType;
import com.loremind.infrastructure.persistence.converter.PrerequisiteListJsonConverter;
import com.loremind.infrastructure.persistence.entity.*;
import com.loremind.infrastructure.persistence.jpa.*;
import com.loremind.infrastructure.transfer.dto.ContentExport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

/**
 * Service d'IMPORT de contenu en mode FUSION.
 * <p>
 * On NE remplace PAS l'existant : chaque entite importee est reinseree avec un
 * NOUVEL id auto-genere, et toutes les references (FK Long et refs faibles
 * String) sont remappees oldId -> newId. Cela permet d'agreger plusieurs exports
 * dans une meme base sans collision, entre Postgres et H2.
 * <p>
 * Algorithme :
 * <ol>
 *   <li>Parse le zip : {@code data.json} -> {@link ContentExport}, binaires gardes en memoire.</li>
 *   <li>Reecrit les images sous LEUR CLE D'ORIGINE (pas de remapping de cle) ;
 *       skip si une ImageJpaEntity avec cette cle existe deja.</li>
 *   <li>Insere top-down en construisant les maps de remapping par type.</li>
 *   <li>2e passe : remappe les references qui pointent vers des types inserees
 *       plus tard (parentId, defaultNodeId, refs faibles String) puis re-save.</li>
 * </ol>
 * Les references vers un id absent des maps (ex. relatedPageId hors export) sont
 * CONSERVEES telles quelles (choix : ne pas perdre d'info, ne jamais planter).
 */
@Service
public class ImportService {

    // (Dé)sérialise les prérequis dans le format "kind" du converter JPA (Prerequisite
    // est scellé, non sérialisable en polymorphe par l'ObjectMapper standard).
    private static final PrerequisiteListJsonConverter PREREQ_CONVERTER = new PrerequisiteListJsonConverter();

    private final GameSystemJpaRepository gameSystemRepo;
    private final LoreJpaRepository loreRepo;
    private final LoreNodeJpaRepository loreNodeRepo;
    private final TemplateJpaRepository templateRepo;
    private final PageJpaRepository pageRepo;
    private final CampaignJpaRepository campaignRepo;
    private final ArcJpaRepository arcRepo;
    private final ChapterJpaRepository chapterRepo;
    private final SceneJpaRepository sceneRepo;
    private final CharacterJpaRepository characterRepo;
    private final NpcJpaRepository npcRepo;
    private final EnemyJpaRepository enemyRepo;
    private final ItemCatalogJpaRepository itemCatalogRepo;
    private final RandomTableJpaRepository randomTableRepo;
    private final PlaythroughJpaRepository playthroughRepo;
    private final SessionJpaRepository sessionRepo;
    private final SessionEntryJpaRepository sessionEntryRepo;
    private final PlaythroughFlagJpaRepository playthroughFlagRepo;
    private final QuestProgressionJpaRepository questProgressionRepo;
    private final ImageImporter imageImporter;
    private final StoredFileImporter storedFileImporter;
    private final ObjectMapper objectMapper;

    public ImportService(GameSystemJpaRepository gameSystemRepo,
                         LoreJpaRepository loreRepo,
                         LoreNodeJpaRepository loreNodeRepo,
                         TemplateJpaRepository templateRepo,
                         PageJpaRepository pageRepo,
                         CampaignJpaRepository campaignRepo,
                         ArcJpaRepository arcRepo,
                         ChapterJpaRepository chapterRepo,
                         SceneJpaRepository sceneRepo,
                         CharacterJpaRepository characterRepo,
                         NpcJpaRepository npcRepo,
                         EnemyJpaRepository enemyRepo,
                         ItemCatalogJpaRepository itemCatalogRepo,
                         RandomTableJpaRepository randomTableRepo,
                         PlaythroughJpaRepository playthroughRepo,
                         SessionJpaRepository sessionRepo,
                         SessionEntryJpaRepository sessionEntryRepo,
                         PlaythroughFlagJpaRepository playthroughFlagRepo,
                         QuestProgressionJpaRepository questProgressionRepo,
                         ImageImporter imageImporter,
                         StoredFileImporter storedFileImporter,
                         ObjectMapper objectMapper) {
        this.gameSystemRepo = gameSystemRepo;
        this.loreRepo = loreRepo;
        this.loreNodeRepo = loreNodeRepo;
        this.templateRepo = templateRepo;
        this.pageRepo = pageRepo;
        this.campaignRepo = campaignRepo;
        this.arcRepo = arcRepo;
        this.chapterRepo = chapterRepo;
        this.sceneRepo = sceneRepo;
        this.characterRepo = characterRepo;
        this.npcRepo = npcRepo;
        this.enemyRepo = enemyRepo;
        this.itemCatalogRepo = itemCatalogRepo;
        this.randomTableRepo = randomTableRepo;
        this.playthroughRepo = playthroughRepo;
        this.sessionRepo = sessionRepo;
        this.sessionEntryRepo = sessionEntryRepo;
        this.playthroughFlagRepo = playthroughFlagRepo;
        this.questProgressionRepo = questProgressionRepo;
        this.imageImporter = imageImporter;
        this.storedFileImporter = storedFileImporter;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ImportResult importZip(InputStream zipStream) {
        // 1. Parse du zip.
        ParsedArchive archive = parseArchive(zipStream);
        ContentExport export = archive.export();

        ImportResult.Builder result = new ImportResult.Builder();

        // 2. Reecriture des images + fichiers (cle preservee).
        imageImporter.importImages(export, archive.imageBinaries(), result);
        storedFileImporter.importFiles(export, archive.fileBinaries(), result);

        // 3. Insertion top-down + maps de remapping.
        Map<Long, Long> gameSystemMap = new HashMap<>();
        Map<Long, Long> loreMap = new HashMap<>();
        Map<Long, Long> loreNodeMap = new HashMap<>();
        Map<Long, Long> templateMap = new HashMap<>();
        Map<Long, Long> pageMap = new HashMap<>();
        Map<Long, Long> campaignMap = new HashMap<>();
        Map<Long, Long> arcMap = new HashMap<>();
        Map<Long, Long> chapterMap = new HashMap<>();
        Map<Long, Long> npcMap = new HashMap<>();
        Map<Long, Long> enemyMap = new HashMap<>();
        Map<Long, Long> characterMap = new HashMap<>();
        Map<Long, Long> sceneMap = new HashMap<>();
        Map<Long, Long> playthroughMap = new HashMap<>();
        Map<Long, Long> sessionMap = new HashMap<>();

        // -- GameSystem
        for (ContentExport.GameSystemDto d : nullSafe(export.gameSystems())) {
            GameSystemJpaEntity e = new GameSystemJpaEntity();
            e.setName(d.name());
            e.setDescription(d.description());
            e.setRulesMarkdown(d.rulesMarkdown());
            e.setCharacterTemplate(d.characterTemplate());
            e.setNpcTemplate(d.npcTemplate());
            e.setEnemyTemplate(d.enemyTemplate());
            e.setFoundryActorType(d.foundryActorType());
            e.setAuthor(d.author());
            e.setPublic(d.isPublic());
            gameSystemMap.put(d.id(), gameSystemRepo.save(e).getId());
        }
        result.count("gameSystems", gameSystemMap.size());

        // -- Lore
        for (ContentExport.LoreDto d : nullSafe(export.lores())) {
            LoreJpaEntity e = new LoreJpaEntity();
            e.setName(d.name());
            e.setDescription(d.description());
            e.setNodeCount(d.nodeCount());
            e.setPageCount(d.pageCount());
            loreMap.put(d.id(), loreRepo.save(e).getId());
        }
        result.count("lores", loreMap.size());

        // -- LoreNode (parentId remappe en 2e passe)
        List<LoreNodeJpaEntity> loreNodesToFix = new ArrayList<>();
        for (ContentExport.LoreNodeDto d : nullSafe(export.loreNodes())) {
            LoreNodeJpaEntity e = new LoreNodeJpaEntity();
            e.setName(d.name());
            e.setIcon(d.icon());
            e.setParentId(d.parentId()); // remappe plus bas
            e.setLoreId(IdRemapper.remapId(loreMap, d.loreId()));
            LoreNodeJpaEntity saved = loreNodeRepo.save(e);
            loreNodeMap.put(d.id(), saved.getId());
            if (d.parentId() != null) loreNodesToFix.add(saved);
        }
        result.count("loreNodes", loreNodeMap.size());

        // -- Template (defaultNodeId remappe en 2e passe)
        List<ContentExport.TemplateDto> templatesWithDefaultNode = new ArrayList<>();
        for (ContentExport.TemplateDto d : nullSafe(export.templates())) {
            TemplateJpaEntity e = new TemplateJpaEntity();
            e.setLoreId(IdRemapper.remapId(loreMap, d.loreId()));
            e.setName(d.name());
            e.setDescription(d.description());
            e.setDefaultNodeId(d.defaultNodeId()); // remappe plus bas
            e.setFields(d.fields());
            templateMap.put(d.id(), templateRepo.save(e).getId());
            if (d.defaultNodeId() != null) templatesWithDefaultNode.add(d);
        }
        result.count("templates", templateMap.size());

        // -- Page (relatedPageIds remappe en 2e passe)
        for (ContentExport.PageDto d : nullSafe(export.pages())) {
            PageJpaEntity e = new PageJpaEntity();
            e.setLoreId(IdRemapper.remapId(loreMap, d.loreId()));
            e.setNodeId(IdRemapper.remapId(loreNodeMap, d.nodeId()));
            e.setTemplateId(IdRemapper.remapId(templateMap, d.templateId()));
            e.setTitle(d.title());
            e.setValues(d.values());
            e.setImageValues(d.imageValues());
            e.setImageFraming(d.imageFraming());
            e.setKeyValueValues(d.keyValueValues());
            e.setTableValues(d.tableValues());
            e.setNotes(d.notes());
            e.setTags(d.tags());
            e.setRelatedPageIds(d.relatedPageIds()); // remappe plus bas
            pageMap.put(d.id(), pageRepo.save(e).getId());
        }
        result.count("pages", pageMap.size());

        // -- Campaign (loreId/gameSystemId String remappes en 2e passe)
        for (ContentExport.CampaignDto d : nullSafe(export.campaigns())) {
            CampaignJpaEntity e = new CampaignJpaEntity();
            e.setName(d.name());
            e.setDescription(d.description());
            e.setArcsCount(d.arcsCount());
            e.setPlayerCount(d.playerCount());
            e.setLoreId(d.loreId());             // remappe plus bas
            e.setGameSystemId(d.gameSystemId()); // remappe plus bas
            campaignMap.put(d.id(), campaignRepo.save(e).getId());
        }
        result.count("campaigns", campaignMap.size());

        // -- Playthrough (Partie) : campaignId remappe
        for (ContentExport.PlaythroughDto d : nullSafe(export.playthroughs())) {
            PlaythroughJpaEntity e = new PlaythroughJpaEntity();
            e.setCampaignId(IdRemapper.remapId(campaignMap, d.campaignId()));
            e.setName(d.name());
            e.setDescription(d.description());
            playthroughMap.put(d.id(), playthroughRepo.save(e).getId());
        }
        result.count("playthroughs", playthroughMap.size());

        // -- Session : campaignId (ref faible String) + playthroughId remappes
        for (ContentExport.SessionDto d : nullSafe(export.sessions())) {
            SessionJpaEntity e = new SessionJpaEntity();
            e.setName(d.name());
            e.setCampaignId(IdRemapper.remapStringId(campaignMap, d.campaignId()));
            e.setPlaythroughId(IdRemapper.remapId(playthroughMap, d.playthroughId()));
            e.setStartedAt(parseDateTime(d.startedAt()));
            e.setEndedAt(parseDateTime(d.endedAt()));
            sessionMap.put(d.id(), sessionRepo.save(e).getId());
        }
        result.count("sessions", sessionMap.size());

        // -- SessionEntry : sessionId (ref faible String) remappe
        int sessionEntryCount = 0;
        for (ContentExport.SessionEntryDto d : nullSafe(export.sessionEntries())) {
            SessionEntryJpaEntity e = new SessionEntryJpaEntity();
            e.setSessionId(IdRemapper.remapStringId(sessionMap, d.sessionId()));
            e.setType(parseEntryType(d.type()));
            e.setContent(d.content());
            e.setOccurredAt(parseDateTime(d.occurredAt()));
            sessionEntryRepo.save(e);
            sessionEntryCount++;
        }
        result.count("sessionEntries", sessionEntryCount);

        // -- PlaythroughFlag : playthroughId remappe (la contrainte unique (playthroughId,name)
        //    ne saute pas, le playthroughId etant neuf).
        int flagCount = 0;
        for (ContentExport.PlaythroughFlagDto d : nullSafe(export.playthroughFlags())) {
            PlaythroughFlagJpaEntity e = new PlaythroughFlagJpaEntity();
            e.setPlaythroughId(IdRemapper.remapId(playthroughMap, d.playthroughId()));
            e.setName(d.name());
            e.setValue(d.value());
            playthroughFlagRepo.save(e);
            flagCount++;
        }
        result.count("playthroughFlags", flagCount);

        // -- Arc (relatedPageIds remappe en 2e passe)
        for (ContentExport.ArcDto d : nullSafe(export.arcs())) {
            ArcJpaEntity e = new ArcJpaEntity();
            e.setName(d.name());
            e.setDescription(d.description());
            e.setCampaignId(IdRemapper.remapId(campaignMap, d.campaignId()));
            e.setOrder(d.order());
            e.setType(IdRemapper.parseArcType(d.type()));
            e.setIcon(d.icon());
            e.setThemes(d.themes());
            e.setStakes(d.stakes());
            e.setGmNotes(d.gmNotes());
            e.setRewards(d.rewards());
            e.setResolution(d.resolution());
            e.setRelatedPageIds(d.relatedPageIds()); // remappe plus bas
            e.setIllustrationImageIds(d.illustrationImageIds());
            arcMap.put(d.id(), arcRepo.save(e).getId());
        }
        result.count("arcs", arcMap.size());

        // -- ItemCatalog (+ items en cascade)
        int catalogCount = 0, itemCount = 0;
        for (ContentExport.ItemCatalogDto d : nullSafe(export.itemCatalogs())) {
            ItemCatalogJpaEntity e = new ItemCatalogJpaEntity();
            e.setName(d.name());
            e.setDescription(d.description());
            e.setIcon(d.icon());
            e.setCampaignId(IdRemapper.remapId(campaignMap, d.campaignId()));
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
            e.setCampaignId(IdRemapper.remapId(campaignMap, d.campaignId()));
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

        // -- Chapter (prerequisites + relatedPageIds remappes en 2e passe)
        for (ContentExport.ChapterDto d : nullSafe(export.chapters())) {
            ChapterJpaEntity e = new ChapterJpaEntity();
            e.setName(d.name());
            e.setDescription(d.description());
            e.setArcId(IdRemapper.remapId(arcMap, d.arcId()));
            e.setOrder(d.order());
            e.setPrerequisites(PREREQ_CONVERTER.convertToEntityAttribute(d.prerequisitesJson())); // remappe plus bas
            e.setIcon(d.icon());
            e.setGmNotes(d.gmNotes());
            e.setPlayerObjectives(d.playerObjectives());
            e.setNarrativeStakes(d.narrativeStakes());
            e.setRelatedPageIds(d.relatedPageIds()); // remappe plus bas
            e.setIllustrationImageIds(d.illustrationImageIds());
            chapterMap.put(d.id(), chapterRepo.save(e).getId());
        }
        result.count("chapters", chapterMap.size());

        // -- QuestProgression : playthroughId + chapterId remappes (chapitres deja inseres ;
        //    contrainte unique (playthroughId, chapterId) preservee car playthroughId neuf).
        int questProgCount = 0;
        for (ContentExport.QuestProgressionDto d : nullSafe(export.questProgressions())) {
            QuestProgressionJpaEntity e = new QuestProgressionJpaEntity();
            e.setPlaythroughId(IdRemapper.remapId(playthroughMap, d.playthroughId()));
            e.setChapterId(IdRemapper.remapId(chapterMap, d.chapterId()));
            e.setStatus(parseProgressionStatus(d.status()));
            questProgressionRepo.save(e);
            questProgCount++;
        }
        result.count("questProgressions", questProgCount);

        // -- Npc (relatedPageIds remappe en 2e passe)
        for (ContentExport.NpcDto d : nullSafe(export.npcs())) {
            NpcJpaEntity e = new NpcJpaEntity();
            e.setName(d.name());
            e.setPortraitImageId(d.portraitImageId());
            e.setHeaderImageId(d.headerImageId());
            e.setValues(d.values());
            e.setImageValues(d.imageValues());
            e.setKeyValueValues(d.keyValueValues());
            e.setCampaignId(IdRemapper.remapId(campaignMap, d.campaignId()));
            e.setRelatedPageIds(d.relatedPageIds()); // remappe plus bas
            e.setFolder(d.folder());
            e.setOrder(d.order());
            npcMap.put(d.id(), npcRepo.save(e).getId());
        }
        result.count("npcs", npcMap.size());

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
            e.setCampaignId(IdRemapper.remapId(campaignMap, d.campaignId()));
            e.setFoundryRef(d.foundryRef()); // ref externe Foundry : conservee telle quelle
            e.setFoundryStats(d.foundryStats());
            e.setOrder(d.order());
            enemyMap.put(d.id(), enemyRepo.save(e).getId());
        }
        result.count("enemies", enemyMap.size());

        // -- Character (playthroughId mis a null : hors perimetre)
        for (ContentExport.CharacterDto d : nullSafe(export.characters())) {
            CharacterJpaEntity e = new CharacterJpaEntity();
            e.setName(d.name());
            e.setPortraitImageId(d.portraitImageId());
            e.setHeaderImageId(d.headerImageId());
            e.setValues(d.values());
            e.setImageValues(d.imageValues());
            e.setKeyValueValues(d.keyValueValues());
            e.setCampaignId(IdRemapper.remapId(campaignMap, d.campaignId()));
            // playthroughId remappe vers la Partie importee (ou null si le jeu n'etait pas
            // dans l'export -> la map est vide). Evite une reference pendante.
            e.setPlaythroughId(playthroughMap.get(d.playthroughId()));
            e.setOrder(d.order());
            characterMap.put(d.id(), characterRepo.save(e).getId());
        }
        result.count("characters", characterMap.size());

        // -- Scene (enemyIds + relatedPageIds + branches remappes en 2e passe)
        for (ContentExport.SceneDto d : nullSafe(export.scenes())) {
            SceneJpaEntity e = new SceneJpaEntity();
            e.setName(d.name());
            e.setDescription(d.description());
            e.setChapterId(IdRemapper.remapId(chapterMap, d.chapterId()));
            e.setOrder(d.order());
            e.setIcon(d.icon());
            e.setLocation(d.location());
            e.setTiming(d.timing());
            e.setAtmosphere(d.atmosphere());
            e.setPlayerNarration(d.playerNarration());
            e.setGmSecretNotes(d.gmSecretNotes());
            e.setChoicesConsequences(d.choicesConsequences());
            e.setCombatDifficulty(d.combatDifficulty());
            e.setEnemies(d.enemies());
            e.setEnemyIds(d.enemyIds());            // remappe plus bas
            e.setRelatedPageIds(d.relatedPageIds()); // remappe plus bas
            e.setIllustrationImageIds(d.illustrationImageIds());
            // Battlemap : ids StoredFile passes tels quels (meme logique que les refs
            // d'images illustration, non remappees). Cf. ImportService doc.
            e.setBattlemapMediaFileId(d.battlemapMediaFileId());
            e.setBattlemapDataFileId(d.battlemapDataFileId());
            e.setBranches(d.branches());             // remappe plus bas
            e.setRooms(d.rooms());                   // Rooms: UUID, non remappes
            sceneMap.put(d.id(), sceneRepo.save(e).getId());
        }
        result.count("scenes", sceneMap.size());

        // 4. 2e PASSE de remapping.

        // LoreNode.parentId
        for (LoreNodeJpaEntity e : loreNodesToFix) {
            Long newParent = loreNodeMap.get(e.getParentId());
            if (newParent != null) {
                e.setParentId(newParent);
                loreNodeRepo.save(e);
            }
        }

        // Template.defaultNodeId
        for (ContentExport.TemplateDto d : templatesWithDefaultNode) {
            Long newTemplateId = templateMap.get(d.id());
            Long newNode = loreNodeMap.get(d.defaultNodeId());
            if (newTemplateId != null && newNode != null) {
                templateRepo.findById(newTemplateId).ifPresent(t -> {
                    t.setDefaultNodeId(newNode);
                    templateRepo.save(t);
                });
            }
        }

        // Campaign.loreId & gameSystemId (refs faibles String -> remap via maps Long).
        for (Long newCampaignId : campaignMap.values()) {
            campaignRepo.findById(newCampaignId).ifPresent(c -> {
                String newLore = IdRemapper.remapStringId(loreMap, c.getLoreId());
                String newGs = IdRemapper.remapStringId(gameSystemMap, c.getGameSystemId());
                c.setLoreId(newLore);
                c.setGameSystemId(newGs);
                campaignRepo.save(c);
            });
        }

        // Page.relatedPageIds
        for (Long newPageId : pageMap.values()) {
            pageRepo.findById(newPageId).ifPresent(p -> {
                p.setRelatedPageIds(IdRemapper.remapStringList(pageMap, p.getRelatedPageIds()));
                pageRepo.save(p);
            });
        }

        // Arc.relatedPageIds
        for (Long newArcId : arcMap.values()) {
            arcRepo.findById(newArcId).ifPresent(a -> {
                a.setRelatedPageIds(IdRemapper.remapStringList(pageMap, a.getRelatedPageIds()));
                arcRepo.save(a);
            });
        }

        // Chapter.relatedPageIds + prerequisites(QuestCompleted -> map Chapter)
        for (Long newChapterId : chapterMap.values()) {
            chapterRepo.findById(newChapterId).ifPresent(c -> {
                c.setRelatedPageIds(IdRemapper.remapStringList(pageMap, c.getRelatedPageIds()));
                c.setPrerequisites(IdRemapper.remapPrerequisites(chapterMap, c.getPrerequisites()));
                chapterRepo.save(c);
            });
        }

        // Npc.relatedPageIds
        for (Long newNpcId : npcMap.values()) {
            npcRepo.findById(newNpcId).ifPresent(n -> {
                n.setRelatedPageIds(IdRemapper.remapStringList(pageMap, n.getRelatedPageIds()));
                npcRepo.save(n);
            });
        }

        // Scene.relatedPageIds + enemyIds(map Enemy) + branches.targetSceneId(map Scene)
        for (Long newSceneId : sceneMap.values()) {
            sceneRepo.findById(newSceneId).ifPresent(s -> {
                s.setRelatedPageIds(IdRemapper.remapStringList(pageMap, s.getRelatedPageIds()));
                s.setEnemyIds(IdRemapper.remapStringList(enemyMap, s.getEnemyIds()));
                s.setBranches(IdRemapper.remapBranches(sceneMap, s.getBranches()));
                sceneRepo.save(s);
            });
        }

        return result.build();
    }

    // ----- Lecture de l'archive -----

    /** Contenu déballé d'un zip d'import : {@code data.json} + binaires images + fichiers. */
    private record ParsedArchive(ContentExport export,
                                 Map<String, byte[]> imageBinaries,
                                 Map<String, byte[]> fileBinaries) {
    }

    /**
     * Déballe le zip : {@code data.json → ContentExport} et {@code images/<clé> → binaire}
     * (le {@code manifest.json} est ignoré, info seulement). Lève si {@code data.json} manque.
     */
    private ParsedArchive parseArchive(InputStream zipStream) {
        ContentExport export = null;
        Map<String, byte[]> imageBinaries = new LinkedHashMap<>(); // storageKey -> binaire
        Map<String, byte[]> fileBinaries = new LinkedHashMap<>();  // storageKey -> binaire
        try (ZipInputStream zip = new ZipInputStream(zipStream)) {
            ZipEntry entry;
            while ((entry = zip.getNextEntry()) != null) {
                String name = entry.getName();
                if ("data.json".equals(name)) {
                    export = objectMapper.readValue(readAll(zip), ContentExport.class);
                } else if (name.startsWith("images/") && !entry.isDirectory()) {
                    // La cle de stockage est le chemin sans le prefixe "images/" du zip,
                    // c'est-a-dire EXACTEMENT le storageKey d'origine ("images/UUID.ext").
                    String storageKey = name.substring("images/".length());
                    imageBinaries.put(storageKey, readAll(zip));
                } else if (name.startsWith("files/") && !entry.isDirectory()) {
                    // Le prefixe zip "files/" enrobe le storageKey, lui-meme "files/UUID.ext" :
                    // on retire UNE fois le prefixe pour retrouver la cle d'origine.
                    String storageKey = name.substring("files/".length());
                    fileBinaries.put(storageKey, readAll(zip));
                }
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Echec de lecture du zip d'import", e);
        }
        if (export == null) {
            throw new IllegalArgumentException("Archive invalide : data.json introuvable");
        }
        return new ParsedArchive(export, imageBinaries, fileBinaries);
    }

    // ----- Helpers divers -----

    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }

    /** Parse un horodatage ISO LocalDateTime, ou null si absent/illisible. */
    private static java.time.LocalDateTime parseDateTime(String s) {
        if (s == null || s.isBlank()) return null;
        try {
            return java.time.LocalDateTime.parse(s.trim());
        } catch (java.time.format.DateTimeParseException e) {
            return null;
        }
    }

    /** Parse un EntryType, repli sur NOTE si inconnu/absent (jamais d'echec). */
    private static EntryType parseEntryType(String s) {
        if (s == null) return EntryType.NOTE;
        try {
            return EntryType.valueOf(s);
        } catch (IllegalArgumentException e) {
            return EntryType.NOTE;
        }
    }

    /** Parse un ProgressionStatus, repli sur NOT_STARTED si inconnu/absent. */
    private static ProgressionStatus parseProgressionStatus(String s) {
        if (s == null) return ProgressionStatus.NOT_STARTED;
        try {
            return ProgressionStatus.valueOf(s);
        } catch (IllegalArgumentException e) {
            return ProgressionStatus.NOT_STARTED;
        }
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        in.transferTo(buffer);
        return buffer.toByteArray();
    }
}
