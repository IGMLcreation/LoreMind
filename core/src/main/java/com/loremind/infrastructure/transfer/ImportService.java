package com.loremind.infrastructure.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.campaigncontext.Prerequisite;
import com.loremind.domain.campaigncontext.SceneBranch;
import com.loremind.domain.images.ports.ImageStorage;
import com.loremind.infrastructure.persistence.converter.PrerequisiteListJsonConverter;
import com.loremind.infrastructure.persistence.entity.*;
import com.loremind.infrastructure.persistence.jpa.*;
import com.loremind.infrastructure.transfer.dto.ContentExport;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
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
    private final ImageJpaRepository imageRepo;
    private final ImageStorage imageStorage;
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
                         ImageJpaRepository imageRepo,
                         ImageStorage imageStorage,
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
        this.imageRepo = imageRepo;
        this.imageStorage = imageStorage;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public ImportResult importZip(InputStream zipStream) {
        // 1. Parse du zip.
        ContentExport export = null;
        Map<String, byte[]> imageBinaries = new LinkedHashMap<>(); // storageKey -> binaire
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
                }
                // manifest.json : ignore a l'import (info seulement).
                zip.closeEntry();
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Echec de lecture du zip d'import", e);
        }
        if (export == null) {
            throw new IllegalArgumentException("Archive invalide : data.json introuvable");
        }

        ImportResult.Builder result = new ImportResult.Builder();

        // 2. Reecriture des images (cle preservee).
        importImages(export, imageBinaries, result);

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

        // -- GameSystem
        for (ContentExport.GameSystemDto d : nullSafe(export.gameSystems())) {
            GameSystemJpaEntity e = new GameSystemJpaEntity();
            e.setName(d.name());
            e.setDescription(d.description());
            e.setRulesMarkdown(d.rulesMarkdown());
            e.setCharacterTemplate(d.characterTemplate());
            e.setNpcTemplate(d.npcTemplate());
            e.setEnemyTemplate(d.enemyTemplate());
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
            e.setLoreId(remapRequired(loreMap, d.loreId()));
            LoreNodeJpaEntity saved = loreNodeRepo.save(e);
            loreNodeMap.put(d.id(), saved.getId());
            if (d.parentId() != null) loreNodesToFix.add(saved);
        }
        result.count("loreNodes", loreNodeMap.size());

        // -- Template (defaultNodeId remappe en 2e passe)
        List<ContentExport.TemplateDto> templatesWithDefaultNode = new ArrayList<>();
        for (ContentExport.TemplateDto d : nullSafe(export.templates())) {
            TemplateJpaEntity e = new TemplateJpaEntity();
            e.setLoreId(remapRequired(loreMap, d.loreId()));
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
            e.setLoreId(remapRequired(loreMap, d.loreId()));
            e.setNodeId(remapRequired(loreNodeMap, d.nodeId()));
            e.setTemplateId(remapNullable(templateMap, d.templateId()));
            e.setTitle(d.title());
            e.setValues(d.values());
            e.setImageValues(d.imageValues());
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
            e.setLoreId(d.loreId());             // remappe plus bas
            e.setGameSystemId(d.gameSystemId()); // remappe plus bas
            campaignMap.put(d.id(), campaignRepo.save(e).getId());
        }
        result.count("campaigns", campaignMap.size());

        // -- Arc (relatedPageIds remappe en 2e passe)
        for (ContentExport.ArcDto d : nullSafe(export.arcs())) {
            ArcJpaEntity e = new ArcJpaEntity();
            e.setName(d.name());
            e.setDescription(d.description());
            e.setCampaignId(remapRequired(campaignMap, d.campaignId()));
            e.setOrder(d.order());
            e.setType(parseArcType(d.type()));
            e.setIcon(d.icon());
            e.setThemes(d.themes());
            e.setStakes(d.stakes());
            e.setGmNotes(d.gmNotes());
            e.setRewards(d.rewards());
            e.setResolution(d.resolution());
            e.setRelatedPageIds(d.relatedPageIds()); // remappe plus bas
            e.setIllustrationImageIds(d.illustrationImageIds());
            e.setMapImageIds(d.mapImageIds());
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
            e.setCampaignId(remapRequired(campaignMap, d.campaignId()));
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
            e.setCampaignId(remapRequired(campaignMap, d.campaignId()));
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
            e.setArcId(remapRequired(arcMap, d.arcId()));
            e.setOrder(d.order());
            e.setPrerequisites(PREREQ_CONVERTER.convertToEntityAttribute(d.prerequisitesJson())); // remappe plus bas
            e.setIcon(d.icon());
            e.setGmNotes(d.gmNotes());
            e.setPlayerObjectives(d.playerObjectives());
            e.setNarrativeStakes(d.narrativeStakes());
            e.setRelatedPageIds(d.relatedPageIds()); // remappe plus bas
            e.setIllustrationImageIds(d.illustrationImageIds());
            e.setMapImageIds(d.mapImageIds());
            chapterMap.put(d.id(), chapterRepo.save(e).getId());
        }
        result.count("chapters", chapterMap.size());

        // -- Npc (relatedPageIds remappe en 2e passe)
        for (ContentExport.NpcDto d : nullSafe(export.npcs())) {
            NpcJpaEntity e = new NpcJpaEntity();
            e.setName(d.name());
            e.setPortraitImageId(d.portraitImageId());
            e.setHeaderImageId(d.headerImageId());
            e.setValues(d.values());
            e.setImageValues(d.imageValues());
            e.setKeyValueValues(d.keyValueValues());
            e.setCampaignId(remapRequired(campaignMap, d.campaignId()));
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
            e.setCampaignId(remapRequired(campaignMap, d.campaignId()));
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
            e.setCampaignId(remapNullable(campaignMap, d.campaignId()));
            e.setPlaythroughId(null); // Playthrough hors perimetre d'export
            e.setOrder(d.order());
            characterMap.put(d.id(), characterRepo.save(e).getId());
        }
        result.count("characters", characterMap.size());

        // -- Scene (enemyIds + relatedPageIds + branches remappes en 2e passe)
        for (ContentExport.SceneDto d : nullSafe(export.scenes())) {
            SceneJpaEntity e = new SceneJpaEntity();
            e.setName(d.name());
            e.setDescription(d.description());
            e.setChapterId(remapRequired(chapterMap, d.chapterId()));
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
            e.setMapImageIds(d.mapImageIds());
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
                String newLore = remapStringId(loreMap, c.getLoreId());
                String newGs = remapStringId(gameSystemMap, c.getGameSystemId());
                c.setLoreId(newLore);
                c.setGameSystemId(newGs);
                campaignRepo.save(c);
            });
        }

        // Page.relatedPageIds
        for (Long newPageId : pageMap.values()) {
            pageRepo.findById(newPageId).ifPresent(p -> {
                p.setRelatedPageIds(remapStringList(pageMap, p.getRelatedPageIds()));
                pageRepo.save(p);
            });
        }

        // Arc.relatedPageIds
        for (Long newArcId : arcMap.values()) {
            arcRepo.findById(newArcId).ifPresent(a -> {
                a.setRelatedPageIds(remapStringList(pageMap, a.getRelatedPageIds()));
                arcRepo.save(a);
            });
        }

        // Chapter.relatedPageIds + prerequisites(QuestCompleted -> map Chapter)
        for (Long newChapterId : chapterMap.values()) {
            chapterRepo.findById(newChapterId).ifPresent(c -> {
                c.setRelatedPageIds(remapStringList(pageMap, c.getRelatedPageIds()));
                c.setPrerequisites(remapPrerequisites(chapterMap, c.getPrerequisites()));
                chapterRepo.save(c);
            });
        }

        // Npc.relatedPageIds
        for (Long newNpcId : npcMap.values()) {
            npcRepo.findById(newNpcId).ifPresent(n -> {
                n.setRelatedPageIds(remapStringList(pageMap, n.getRelatedPageIds()));
                npcRepo.save(n);
            });
        }

        // Scene.relatedPageIds + enemyIds(map Enemy) + branches.targetSceneId(map Scene)
        for (Long newSceneId : sceneMap.values()) {
            sceneRepo.findById(newSceneId).ifPresent(s -> {
                s.setRelatedPageIds(remapStringList(pageMap, s.getRelatedPageIds()));
                s.setEnemyIds(remapStringList(enemyMap, s.getEnemyIds()));
                s.setBranches(remapBranches(sceneMap, s.getBranches()));
                sceneRepo.save(s);
            });
        }

        return result.build();
    }

    // ----- Images -----

    private void importImages(ContentExport export,
                              Map<String, byte[]> imageBinaries,
                              ImportResult.Builder result) {
        // Index des metadonnees d'image par cle (depuis le data.json).
        Map<String, ContentExport.ImageDto> metaByKey = new HashMap<>();
        for (ContentExport.ImageDto img : nullSafe(export.images())) {
            if (img.storageKey() != null) metaByKey.put(img.storageKey(), img);
        }

        for (Map.Entry<String, byte[]> bin : imageBinaries.entrySet()) {
            String storageKey = bin.getKey();
            byte[] data = bin.getValue();
            if (imageRepo.findByStorageKey(storageKey).isPresent()) {
                // Image deja presente : on reutilise, pas de reupload (eviter doublon).
                result.imageReused();
                continue;
            }
            ContentExport.ImageDto meta = metaByKey.get(storageKey);
            String contentType = meta != null && meta.contentType() != null
                    ? meta.contentType() : guessContentType(storageKey);
            long size = meta != null ? meta.sizeBytes() : data.length;

            imageStorage.store(storageKey, contentType, new ByteArrayInputStream(data), data.length);

            ImageJpaEntity e = new ImageJpaEntity();
            e.setFilename(meta != null && meta.filename() != null
                    ? meta.filename() : fileNameOf(storageKey));
            e.setContentType(contentType);
            e.setSizeBytes(size);
            e.setStorageKey(storageKey);
            imageRepo.save(e);
            result.imageUploaded();
        }
    }

    // ----- Helpers de remapping -----

    /** Remap obligatoire d'une FK Long : si absente de la map, on garde l'ancienne valeur. */
    private Long remapRequired(Map<Long, Long> map, Long oldId) {
        if (oldId == null) return null;
        return map.getOrDefault(oldId, oldId);
    }

    /** Remap d'une FK Long nullable : null reste null. */
    private Long remapNullable(Map<Long, Long> map, Long oldId) {
        if (oldId == null) return null;
        return map.getOrDefault(oldId, oldId);
    }

    /** Remap d'un id stocke en String ("oldLong" -> "newLong") via une map Long. */
    private String remapStringId(Map<Long, Long> map, String oldId) {
        if (oldId == null || oldId.isBlank()) return oldId;
        try {
            Long newId = map.get(Long.parseLong(oldId.trim()));
            return newId != null ? String.valueOf(newId) : oldId;
        } catch (NumberFormatException ex) {
            return oldId; // pas un Long : on laisse tel quel
        }
    }

    private List<String> remapStringList(Map<Long, Long> map, List<String> ids) {
        if (ids == null) return null;
        List<String> out = new ArrayList<>(ids.size());
        for (String id : ids) out.add(remapStringId(map, id));
        return out;
    }

    private List<Prerequisite> remapPrerequisites(Map<Long, Long> chapterMap, List<Prerequisite> prereqs) {
        if (prereqs == null) return null;
        List<Prerequisite> out = new ArrayList<>(prereqs.size());
        for (Prerequisite p : prereqs) {
            if (p instanceof Prerequisite.QuestCompleted qc) {
                out.add(new Prerequisite.QuestCompleted(remapStringId(chapterMap, qc.questId())));
            } else {
                out.add(p); // FlagSet / SessionReached : inchanges
            }
        }
        return out;
    }

    private List<SceneBranch> remapBranches(Map<Long, Long> sceneMap, List<SceneBranch> branches) {
        if (branches == null) return null;
        List<SceneBranch> out = new ArrayList<>(branches.size());
        for (SceneBranch b : branches) {
            out.add(new SceneBranch(b.label(), remapStringId(sceneMap, b.targetSceneId()), b.condition()));
        }
        return out;
    }

    private com.loremind.domain.campaigncontext.ArcType parseArcType(String type) {
        if (type == null) return com.loremind.domain.campaigncontext.ArcType.LINEAR;
        try {
            return com.loremind.domain.campaigncontext.ArcType.valueOf(type);
        } catch (IllegalArgumentException ex) {
            return com.loremind.domain.campaigncontext.ArcType.LINEAR;
        }
    }

    // ----- Helpers divers -----

    private static <T> List<T> nullSafe(List<T> list) {
        return list != null ? list : List.of();
    }

    private static byte[] readAll(InputStream in) throws IOException {
        ByteArrayOutputStream buffer = new ByteArrayOutputStream();
        in.transferTo(buffer);
        return buffer.toByteArray();
    }

    private static String fileNameOf(String storageKey) {
        int slash = storageKey.lastIndexOf('/');
        return slash >= 0 ? storageKey.substring(slash + 1) : storageKey;
    }

    private static String guessContentType(String storageKey) {
        String lower = storageKey.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".gif")) return "image/gif";
        if (lower.endsWith(".webp")) return "image/webp";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        return "application/octet-stream";
    }
}
