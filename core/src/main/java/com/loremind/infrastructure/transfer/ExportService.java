package com.loremind.infrastructure.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.loremind.domain.files.ports.FileStorage;
import com.loremind.domain.images.ports.ImageStorage;
import com.loremind.infrastructure.persistence.converter.PrerequisiteListJsonConverter;
import com.loremind.infrastructure.persistence.entity.*;
import com.loremind.infrastructure.persistence.jpa.*;
import com.loremind.infrastructure.transfer.dto.ContentExport;
import org.springframework.boot.info.BuildProperties;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.ArrayList;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Set;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Service d'EXPORT du "contenu" en format logique (JSON) portable.
 * <p>
 * Construit un {@link ContentExport} a partir des entites JPA puis le serialise
 * dans un .zip contenant :
 * <ul>
 *   <li>{@code manifest.json} — metadonnees (version de format, version app, date)</li>
 *   <li>{@code data.json}     — tout le contenu (pretty-printed)</li>
 *   <li>{@code images/<storageKey>} — un binaire par image referencee</li>
 * </ul>
 * Volontairement DECOUPLE de la base : c'est un export logique, pas un dump,
 * pour fonctionner entre Postgres (Docker) et H2 (local).
 */
@Service
public class ExportService {

    private static final int FORMAT_VERSION = 1;

    // Réutilise le converter JPA pour (dé)sérialiser les prérequis dans le MÊME
    // format que la base (discriminant "kind"), au lieu de Jackson polymorphe.
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
    private final StoredFileJpaRepository storedFileRepo;
    private final FileStorage fileStorage;
    private final ObjectMapper objectMapper;
    private final String appVersion;

    public ExportService(GameSystemJpaRepository gameSystemRepo,
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
                         StoredFileJpaRepository storedFileRepo,
                         FileStorage fileStorage,
                         ObjectMapper objectMapper,
                         @Nullable BuildProperties buildProperties) {
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
        this.storedFileRepo = storedFileRepo;
        this.fileStorage = fileStorage;
        this.objectMapper = objectMapper;
        this.appVersion = buildProperties != null ? buildProperties.getVersion() : "dev";
    }

    /**
     * Charge toutes les entites du perimetre et les mappe vers les DTO plats.
     *
     * @param exportedAt horodatage ISO stampe par la couche appelante (controller)
     */
    public ContentExport buildExport(String exportedAt) {
        ContentExport.Manifest manifest =
                new ContentExport.Manifest(FORMAT_VERSION, appVersion, exportedAt);

        List<ContentExport.GameSystemDto> gameSystems = gameSystemRepo.findAll().stream()
                .map(this::toGameSystemDto).toList();
        List<ContentExport.LoreDto> lores = loreRepo.findAll().stream()
                .map(this::toLoreDto).toList();
        List<ContentExport.LoreNodeDto> loreNodes = loreNodeRepo.findAll().stream()
                .map(this::toLoreNodeDto).toList();
        List<ContentExport.TemplateDto> templates = templateRepo.findAll().stream()
                .map(this::toTemplateDto).toList();
        List<ContentExport.PageDto> pages = pageRepo.findAll().stream()
                .map(this::toPageDto).toList();
        List<ContentExport.CampaignDto> campaigns = campaignRepo.findAll().stream()
                .map(this::toCampaignDto).toList();
        List<ContentExport.ArcDto> arcs = arcRepo.findAll().stream()
                .map(this::toArcDto).toList();
        List<ContentExport.ChapterDto> chapters = chapterRepo.findAll().stream()
                .map(this::toChapterDto).toList();
        List<ContentExport.SceneDto> scenes = sceneRepo.findAll().stream()
                .map(this::toSceneDto).toList();
        List<ContentExport.CharacterDto> characters = characterRepo.findAll().stream()
                .map(this::toCharacterDto).toList();
        List<ContentExport.NpcDto> npcs = npcRepo.findAll().stream()
                .map(this::toNpcDto).toList();
        List<ContentExport.EnemyDto> enemies = enemyRepo.findAll().stream()
                .map(this::toEnemyDto).toList();
        List<ContentExport.ItemCatalogDto> itemCatalogs = itemCatalogRepo.findAll().stream()
                .map(this::toItemCatalogDto).toList();
        List<ContentExport.RandomTableDto> randomTables = randomTableRepo.findAll().stream()
                .map(this::toRandomTableDto).toList();
        List<ContentExport.ImageDto> images = imageRepo.findAll().stream()
                .map(this::toImageDto).toList();
        List<ContentExport.StoredFileDto> storedFiles = storedFileRepo.findAll().stream()
                .map(this::toStoredFileDto).toList();

        return new ContentExport(manifest, gameSystems, lores, loreNodes, templates,
                pages, campaigns, arcs, chapters, scenes, characters, npcs, enemies,
                itemCatalogs, randomTables, images, storedFiles);
    }

    /**
     * Serialise un export dans le flux fourni au format .zip.
     * <p>
     * Les binaires d'images ne sont ecrits que pour les storageKeys REFERENCES
     * par les entites exportees (illustration/map/portrait/header/imageValues),
     * pas pour toute la table images — on evite de trimballer des orphelins.
     */
    public void writeZip(ContentExport export, OutputStream out) {
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            // manifest.json
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(objectMapper.writerWithDefaultPrettyPrinter()
                    .writeValueAsBytes(export.manifest()));
            zip.closeEntry();

            // data.json
            zip.putNextEntry(new ZipEntry("data.json"));
            zip.write(objectMapper.copy()
                    .enable(SerializationFeature.INDENT_OUTPUT)
                    .writeValueAsBytes(export));
            zip.closeEntry();

            // Binaires images : uniquement ceux reellement references.
            Set<String> referenced = collectReferencedStorageKeys(export);
            Set<String> written = new LinkedHashSet<>();
            for (String key : referenced) {
                if (key == null || key.isBlank() || !written.add(key)) {
                    continue;
                }
                try (InputStream data = imageStorage.download(key)) {
                    if (data == null) {
                        continue; // cle orpheline : on ignore silencieusement
                    }
                    zip.putNextEntry(new ZipEntry("images/" + key));
                    data.transferTo(zip);
                    zip.closeEntry();
                }
            }

            // Binaires fichiers (battlemaps : media + sidecar) : ceux references par
            // les scenes. Stockes a part sous "files/<storageKey>".
            Set<String> referencedFiles = collectReferencedFileStorageKeys(export);
            Set<String> filesWritten = new LinkedHashSet<>();
            for (String key : referencedFiles) {
                if (key == null || key.isBlank() || !filesWritten.add(key)) {
                    continue;
                }
                try (InputStream data = fileStorage.download(key)) {
                    if (data == null) {
                        continue; // cle orpheline : on ignore silencieusement
                    }
                    zip.putNextEntry(new ZipEntry("files/" + key));
                    data.transferTo(zip);
                    zip.closeEntry();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Echec de la generation du zip d'export", e);
        }
    }

    /**
     * Collecte tous les storageKeys references par le contenu exporte :
     * Arc/Chapter/Scene (illustration + map), Character/Npc/Enemy
     * (portrait + header + imageValues), Page (imageValues).
     */
    private Set<String> collectReferencedStorageKeys(ContentExport export) {
        Set<String> keys = new LinkedHashSet<>();
        for (ContentExport.ArcDto a : export.arcs()) {
            addAll(keys, a.illustrationImageIds());
        }
        for (ContentExport.ChapterDto c : export.chapters()) {
            addAll(keys, c.illustrationImageIds());
        }
        for (ContentExport.SceneDto s : export.scenes()) {
            addAll(keys, s.illustrationImageIds());
        }
        for (ContentExport.CharacterDto c : export.characters()) {
            add(keys, c.portraitImageId());
            add(keys, c.headerImageId());
            addImageValues(keys, c.imageValues());
        }
        for (ContentExport.NpcDto n : export.npcs()) {
            add(keys, n.portraitImageId());
            add(keys, n.headerImageId());
            addImageValues(keys, n.imageValues());
        }
        for (ContentExport.EnemyDto e : export.enemies()) {
            add(keys, e.portraitImageId());
            add(keys, e.headerImageId());
            addImageValues(keys, e.imageValues());
        }
        for (ContentExport.PageDto p : export.pages()) {
            addImageValues(keys, p.imageValues());
        }
        return keys;
    }

    /**
     * Collecte les storageKeys des fichiers (battlemaps) references par les scenes.
     * Resout l'ID StoredFile porte par la scene -> storageKey via l'index storedFiles.
     */
    private Set<String> collectReferencedFileStorageKeys(ContentExport export) {
        java.util.Map<String, String> keyById = new java.util.HashMap<>();
        for (ContentExport.StoredFileDto f : export.storedFiles()) {
            if (f.id() != null) keyById.put(f.id().toString(), f.storageKey());
        }
        Set<String> keys = new LinkedHashSet<>();
        for (ContentExport.SceneDto s : export.scenes()) {
            addFileKey(keys, keyById, s.battlemapMediaFileId());
            addFileKey(keys, keyById, s.battlemapDataFileId());
        }
        return keys;
    }

    private void addFileKey(Set<String> keys, java.util.Map<String, String> keyById, String fileId) {
        if (fileId == null || fileId.isBlank()) return;
        String key = keyById.get(fileId);
        if (key != null && !key.isBlank()) keys.add(key);
    }

    private void add(Set<String> keys, String key) {
        if (key != null && !key.isBlank()) keys.add(key);
    }

    private void addAll(Set<String> keys, List<String> list) {
        if (list != null) list.forEach(k -> add(keys, k));
    }

    private void addImageValues(Set<String> keys, java.util.Map<String, List<String>> imageValues) {
        if (imageValues != null) imageValues.values().forEach(l -> addAll(keys, l));
    }

    // ----- Mappers entite -> DTO -----

    private ContentExport.GameSystemDto toGameSystemDto(GameSystemJpaEntity e) {
        return new ContentExport.GameSystemDto(e.getId(), e.getName(), e.getDescription(),
                e.getRulesMarkdown(), e.getCharacterTemplate(), e.getNpcTemplate(),
                e.getEnemyTemplate(), e.getFoundryActorType(), e.getAuthor(), e.isPublic());
    }

    private ContentExport.LoreDto toLoreDto(LoreJpaEntity e) {
        return new ContentExport.LoreDto(e.getId(), e.getName(), e.getDescription(),
                e.getNodeCount(), e.getPageCount());
    }

    private ContentExport.LoreNodeDto toLoreNodeDto(LoreNodeJpaEntity e) {
        return new ContentExport.LoreNodeDto(e.getId(), e.getName(), e.getIcon(),
                e.getParentId(), e.getLoreId());
    }

    private ContentExport.TemplateDto toTemplateDto(TemplateJpaEntity e) {
        return new ContentExport.TemplateDto(e.getId(), e.getLoreId(), e.getName(),
                e.getDescription(), e.getDefaultNodeId(), e.getFields());
    }

    private ContentExport.PageDto toPageDto(PageJpaEntity e) {
        return new ContentExport.PageDto(e.getId(), e.getLoreId(), e.getNodeId(),
                e.getTemplateId(), e.getTitle(), e.getValues(), e.getImageValues(),
                e.getKeyValueValues(), e.getTableValues(), e.getNotes(), e.getTags(),
                e.getRelatedPageIds());
    }

    private ContentExport.CampaignDto toCampaignDto(CampaignJpaEntity e) {
        return new ContentExport.CampaignDto(e.getId(), e.getName(), e.getDescription(),
                e.getArcsCount(), e.getLoreId(), e.getGameSystemId());
    }

    private ContentExport.ArcDto toArcDto(ArcJpaEntity e) {
        return new ContentExport.ArcDto(e.getId(), e.getName(), e.getDescription(),
                e.getCampaignId(), e.getOrder(),
                e.getType() != null ? e.getType().name() : null,
                e.getIcon(), e.getThemes(), e.getStakes(), e.getGmNotes(),
                e.getRewards(), e.getResolution(), e.getRelatedPageIds(),
                e.getIllustrationImageIds());
    }

    private ContentExport.ChapterDto toChapterDto(ChapterJpaEntity e) {
        return new ContentExport.ChapterDto(e.getId(), e.getName(), e.getDescription(),
                e.getArcId(), e.getOrder(), PREREQ_CONVERTER.convertToDatabaseColumn(e.getPrerequisites()), e.getIcon(),
                e.getGmNotes(), e.getPlayerObjectives(), e.getNarrativeStakes(),
                e.getRelatedPageIds(), e.getIllustrationImageIds());
    }

    private ContentExport.SceneDto toSceneDto(SceneJpaEntity e) {
        return new ContentExport.SceneDto(e.getId(), e.getName(), e.getDescription(),
                e.getChapterId(), e.getOrder(), e.getIcon(), e.getLocation(),
                e.getTiming(), e.getAtmosphere(), e.getPlayerNarration(),
                e.getGmSecretNotes(), e.getChoicesConsequences(), e.getCombatDifficulty(),
                e.getEnemies(), e.getEnemyIds(), e.getRelatedPageIds(),
                e.getIllustrationImageIds(), e.getBattlemapMediaFileId(),
                e.getBattlemapDataFileId(), e.getBranches(), e.getRooms());
    }

    private ContentExport.CharacterDto toCharacterDto(CharacterJpaEntity e) {
        return new ContentExport.CharacterDto(e.getId(), e.getName(), e.getPortraitImageId(),
                e.getHeaderImageId(), e.getValues(), e.getImageValues(), e.getKeyValueValues(),
                e.getCampaignId(), e.getPlaythroughId(), e.getOrder());
    }

    private ContentExport.NpcDto toNpcDto(NpcJpaEntity e) {
        return new ContentExport.NpcDto(e.getId(), e.getName(), e.getPortraitImageId(),
                e.getHeaderImageId(), e.getValues(), e.getImageValues(), e.getKeyValueValues(),
                e.getCampaignId(), e.getRelatedPageIds(), e.getFolder(), e.getOrder());
    }

    private ContentExport.EnemyDto toEnemyDto(EnemyJpaEntity e) {
        return new ContentExport.EnemyDto(e.getId(), e.getName(), e.getLevel(), e.getFolder(),
                e.getPortraitImageId(), e.getHeaderImageId(), e.getValues(), e.getImageValues(),
                e.getKeyValueValues(), e.getCampaignId(), e.getFoundryRef(), e.getFoundryStats(), e.getOrder());
    }

    private ContentExport.ItemCatalogDto toItemCatalogDto(ItemCatalogJpaEntity e) {
        List<ContentExport.CatalogItemDto> items = new ArrayList<>();
        if (e.getItems() != null) {
            for (CatalogItemJpaEntity i : e.getItems()) {
                items.add(new ContentExport.CatalogItemDto(i.getId(), i.getName(),
                        i.getPrice(), i.getCategory(), i.getDescription(), i.getPosition()));
            }
        }
        return new ContentExport.ItemCatalogDto(e.getId(), e.getName(), e.getDescription(),
                e.getIcon(), e.getCampaignId(), e.getOrder(), items);
    }

    private ContentExport.RandomTableDto toRandomTableDto(RandomTableJpaEntity e) {
        List<ContentExport.RandomTableEntryDto> entries = new ArrayList<>();
        if (e.getEntries() != null) {
            for (RandomTableEntryJpaEntity en : e.getEntries()) {
                entries.add(new ContentExport.RandomTableEntryDto(en.getId(), en.getMinRoll(),
                        en.getMaxRoll(), en.getLabel(), en.getDetail(), en.getPosition()));
            }
        }
        return new ContentExport.RandomTableDto(e.getId(), e.getName(), e.getDescription(),
                e.getDiceFormula(), e.getIcon(), e.getCampaignId(), e.getOrder(), entries);
    }

    private ContentExport.ImageDto toImageDto(ImageJpaEntity e) {
        return new ContentExport.ImageDto(e.getId(), e.getFilename(), e.getContentType(),
                e.getSizeBytes(), e.getStorageKey());
    }

    private ContentExport.StoredFileDto toStoredFileDto(StoredFileJpaEntity e) {
        return new ContentExport.StoredFileDto(e.getId(), e.getFilename(), e.getContentType(),
                e.getSizeBytes(), e.getStorageKey());
    }
}
