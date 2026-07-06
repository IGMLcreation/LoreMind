package com.loremind.infrastructure.transfer;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.loremind.domain.campaigncontext.structure.Room;
import com.loremind.domain.files.ports.FileStorage;
import com.loremind.domain.images.ports.ImageStorage;
import com.loremind.infrastructure.persistence.converter.PrerequisiteListJsonConverter;
import com.loremind.infrastructure.persistence.converter.QuestNodeListJsonConverter;
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

    private static final int FORMAT_VERSION = 2;

    // Réutilise le converter JPA pour (dé)sérialiser les prérequis dans le MÊME
    // format que la base (discriminant "kind"), au lieu de Jackson polymorphe.
    private static final PrerequisiteListJsonConverter PREREQ_CONVERTER = new PrerequisiteListJsonConverter();
    private static final QuestNodeListJsonConverter NODE_CONVERTER = new QuestNodeListJsonConverter();

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
    private final PlaythroughJpaRepository playthroughRepo;
    private final SessionJpaRepository sessionRepo;
    private final SessionEntryJpaRepository sessionEntryRepo;
    private final PlaythroughFlagJpaRepository playthroughFlagRepo;
    private final QuestProgressionJpaRepository questProgressionRepo;
    private final QuestJpaRepository questRepo;
    private final ClockJpaRepository clockRepo;
    private final FrontJpaRepository frontRepo;
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
                         PlaythroughJpaRepository playthroughRepo,
                         SessionJpaRepository sessionRepo,
                         SessionEntryJpaRepository sessionEntryRepo,
                         PlaythroughFlagJpaRepository playthroughFlagRepo,
                         QuestProgressionJpaRepository questProgressionRepo,
                         QuestJpaRepository questRepo,
                         ClockJpaRepository clockRepo,
                         FrontJpaRepository frontRepo,
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
        this.playthroughRepo = playthroughRepo;
        this.sessionRepo = sessionRepo;
        this.sessionEntryRepo = sessionEntryRepo;
        this.playthroughFlagRepo = playthroughFlagRepo;
        this.questProgressionRepo = questProgressionRepo;
        this.questRepo = questRepo;
        this.clockRepo = clockRepo;
        this.frontRepo = frontRepo;
        this.objectMapper = objectMapper;
        this.appVersion = buildProperties != null ? buildProperties.getVersion() : "dev";
    }

    /** Sauvegarde complète (rétro-compat) : tout le contenu de la base. */
    public ContentExport buildExport(String exportedAt) {
        return buildExport(exportedAt, ExportRequest.full());
    }

    /**
     * Construit l'export selon le périmètre demandé : {@link ExportRequest#isFull() sauvegarde
     * complète} (toute la base) ou export ciblé d'une campagne et de sa clôture.
     *
     * @param exportedAt horodatage ISO stampe par la couche appelante (controller)
     */
    public ContentExport buildExport(String exportedAt, ExportRequest req) {
        return req.isFull() ? buildFullExport(exportedAt) : buildCampaignExport(exportedAt, req);
    }

    private ContentExport buildFullExport(String exportedAt) {
        ContentExport.Manifest manifest =
                new ContentExport.Manifest(FORMAT_VERSION, appVersion, exportedAt, "complète");
        return new ContentExport(manifest,
                map(gameSystemRepo.findAll(), this::toGameSystemDto),
                map(loreRepo.findAll(), this::toLoreDto),
                map(loreNodeRepo.findAll(), this::toLoreNodeDto),
                map(templateRepo.findAll(), this::toTemplateDto),
                map(pageRepo.findAll(), this::toPageDto),
                map(campaignRepo.findAll(), this::toCampaignDto),
                map(arcRepo.findAll(), this::toArcDto),
                map(chapterRepo.findAll(), this::toChapterDto),
                map(sceneRepo.findAll(), this::toSceneDto),
                map(characterRepo.findAll(), this::toCharacterDto),
                map(npcRepo.findAll(), this::toNpcDto),
                map(enemyRepo.findAll(), this::toEnemyDto),
                map(itemCatalogRepo.findAll(), this::toItemCatalogDto),
                map(randomTableRepo.findAll(), this::toRandomTableDto),
                map(imageRepo.findAll(), this::toImageDto),
                map(storedFileRepo.findAll(), this::toStoredFileDto),
                map(playthroughRepo.findAll(), this::toPlaythroughDto),
                map(sessionRepo.findAll(), this::toSessionDto),
                map(sessionEntryRepo.findAll(), this::toSessionEntryDto),
                map(playthroughFlagRepo.findAll(), this::toFlagDto),
                map(questProgressionRepo.findAll(), this::toQuestProgressionDto),
                map(questRepo.findAll(), this::toQuestDto),
                map(clockRepo.findAll(), this::toClockDto),
                map(frontRepo.findAll(), this::toFrontDto));
    }

    /**
     * Export CIBLÉ : la campagne et sa clôture (arcs → chapitres → scènes, PNJ, ennemis,
     * catalogues, tables, système de jeu lié), plus — selon les options — son univers (lore)
     * et son espace de jeu (parties → sessions/journal/flags/quêtes + feuilles de perso).
     * Les images/fichiers exportés sont uniquement ceux RÉFÉRENCÉS par la clôture.
     */
    private ContentExport buildCampaignExport(String exportedAt, ExportRequest req) {
        Long cid = req.campaignId();
        CampaignJpaEntity campaign = campaignRepo.findById(cid)
                .orElseThrow(() -> new java.util.NoSuchElementException("Campagne introuvable : " + cid));

        // Prep : clôture structurelle de la campagne.
        List<ArcJpaEntity> arcEntities = arcRepo.findByCampaignId(cid);
        List<ChapterJpaEntity> chapterEntities = arcEntities.stream()
                .flatMap(a -> chapterRepo.findByArcId(a.getId()).stream()).toList();
        List<SceneJpaEntity> sceneEntities = chapterEntities.stream()
                .flatMap(c -> sceneRepo.findByChapterId(c.getId()).stream()).toList();
        List<NpcJpaEntity> npcEntities = npcRepo.findByCampaignIdOrderByOrderAsc(cid);
        List<EnemyJpaEntity> enemyEntities = enemyRepo.findByCampaignIdOrderByOrderAsc(cid);
        List<ItemCatalogJpaEntity> catalogEntities = itemCatalogRepo.findByCampaignIdOrderByOrderAsc(cid);
        List<RandomTableJpaEntity> tableEntities = randomTableRepo.findByCampaignIdOrderByOrderAsc(cid);

        // Système de jeu lié : TOUJOURS inclus (templates/PDF en dépendent).
        List<GameSystemJpaEntity> gsEntities = singleton(gameSystemRepo, parseLongOrNull(campaign.getGameSystemId()));

        // Univers (lore) lié : optionnel.
        Long lid = req.includeLore() ? parseLongOrNull(campaign.getLoreId()) : null;
        List<LoreJpaEntity> loreEntities = lid != null ? singleton(loreRepo, lid) : List.of();
        List<LoreNodeJpaEntity> loreNodeEntities = lid != null ? loreNodeRepo.findByLoreId(lid) : List.of();
        List<TemplateJpaEntity> templateEntities = lid != null ? templateRepo.findByLoreId(lid) : List.of();
        List<PageJpaEntity> pageEntities = lid != null ? pageRepo.findByLoreId(lid) : List.of();

        // Espace de jeu : optionnel. Les feuilles de perso appartiennent à une Partie,
        // donc « sans jeu » = sans feuilles de perso.
        List<PlaythroughJpaEntity> ptEntities = req.includePlay() ? playthroughRepo.findByCampaignId(cid) : List.of();
        List<SessionJpaEntity> sessionEntities = ptEntities.stream()
                .flatMap(p -> sessionRepo.findByPlaythroughIdOrderByStartedAtDesc(p.getId()).stream()).toList();
        List<SessionEntryJpaEntity> entryEntities = sessionEntities.stream()
                .flatMap(s -> sessionEntryRepo.findBySessionIdOrderByOccurredAtAsc(String.valueOf(s.getId())).stream()).toList();
        List<PlaythroughFlagJpaEntity> flagEntities = ptEntities.stream()
                .flatMap(p -> playthroughFlagRepo.findByPlaythroughId(p.getId()).stream()).toList();
        List<QuestProgressionJpaEntity> questEntities = ptEntities.stream()
                .flatMap(p -> questProgressionRepo.findByPlaythroughId(p.getId()).stream()).toList();
        List<ClockJpaEntity> clockEntities = ptEntities.stream()
                .flatMap(p -> clockRepo.findByPlaythroughIdOrderByOrderAsc(p.getId()).stream()).toList();
        List<FrontJpaEntity> frontEntities = ptEntities.stream()
                .flatMap(p -> frontRepo.findByPlaythroughIdOrderByOrderAsc(p.getId()).stream()).toList();
        List<CharacterJpaEntity> characterEntities = ptEntities.stream()
                .flatMap(p -> characterRepo.findByPlaythroughIdOrderByOrderAsc(p.getId()).stream()).toList();

        // Quêtes de la campagne (Niveau 1) — toujours incluses dans la clôture.
        List<QuestJpaEntity> campaignQuests = questRepo.findByCampaignId(cid);

        // Images/fichiers : uniquement les binaires RÉFÉRENCÉS par la clôture (si option active).
        List<ImageJpaEntity> imageEntities = List.of();
        List<StoredFileJpaEntity> fileEntities = List.of();
        if (req.includeImages()) {
            Set<String> imageRefs = new LinkedHashSet<>();
            arcEntities.forEach(a -> addAll(imageRefs, a.getIllustrationImageIds()));
            chapterEntities.forEach(c -> addAll(imageRefs, c.getIllustrationImageIds()));
            campaignQuests.forEach(q -> addAll(imageRefs, q.getIllustrationImageIds()));
            sceneEntities.forEach(s -> addAll(imageRefs, s.getIllustrationImageIds()));
            sceneEntities.forEach(s -> addRoomImageRefs(imageRefs, s.getRooms()));
            npcEntities.forEach(n -> { add(imageRefs, n.getPortraitImageId()); add(imageRefs, n.getHeaderImageId()); addImageValues(imageRefs, n.getImageValues()); });
            enemyEntities.forEach(e -> { add(imageRefs, e.getPortraitImageId()); add(imageRefs, e.getHeaderImageId()); addImageValues(imageRefs, e.getImageValues()); });
            characterEntities.forEach(c -> { add(imageRefs, c.getPortraitImageId()); add(imageRefs, c.getHeaderImageId()); addImageValues(imageRefs, c.getImageValues()); });
            pageEntities.forEach(p -> addImageValues(imageRefs, p.getImageValues()));
            imageEntities = imageRefs.stream()
                    .map(ExportService::parseLongOrNull).filter(java.util.Objects::nonNull)
                    .map(id -> imageRepo.findById(id).orElse(null)).filter(java.util.Objects::nonNull)
                    .distinct().toList();

            Set<Long> fileRefs = new LinkedHashSet<>();
            sceneEntities.forEach(s -> {
                if (s.getBattlemaps() == null) return;
                s.getBattlemaps().forEach(bm -> { addLong(fileRefs, bm.mediaFileId()); addLong(fileRefs, bm.dataFileId()); });
            });
            fileEntities = fileRefs.stream()
                    .map(id -> storedFileRepo.findById(id).orElse(null)).filter(java.util.Objects::nonNull).toList();
        }

        // Campaign DTO : si le lore n'est pas exporté, on neutralise loreId (évite une
        // référence pendante vers un univers absent à l'import).
        ContentExport.CampaignDto campaignDto = toCampaignDto(campaign);
        if (!req.includeLore()) {
            campaignDto = new ContentExport.CampaignDto(campaignDto.id(), campaignDto.name(),
                    campaignDto.description(), campaignDto.arcsCount(), campaignDto.playerCount(),
                    null, campaignDto.gameSystemId());
        }

        ContentExport.Manifest manifest =
                new ContentExport.Manifest(FORMAT_VERSION, appVersion, exportedAt, campaign.getName());
        return new ContentExport(manifest,
                map(gsEntities, this::toGameSystemDto),
                map(loreEntities, this::toLoreDto),
                map(loreNodeEntities, this::toLoreNodeDto),
                map(templateEntities, this::toTemplateDto),
                map(pageEntities, this::toPageDto),
                List.of(campaignDto),
                map(arcEntities, this::toArcDto),
                map(chapterEntities, this::toChapterDto),
                map(sceneEntities, this::toSceneDto),
                map(characterEntities, this::toCharacterDto),
                map(npcEntities, this::toNpcDto),
                map(enemyEntities, this::toEnemyDto),
                map(catalogEntities, this::toItemCatalogDto),
                map(tableEntities, this::toRandomTableDto),
                map(imageEntities, this::toImageDto),
                map(fileEntities, this::toStoredFileDto),
                map(ptEntities, this::toPlaythroughDto),
                map(sessionEntities, this::toSessionDto),
                map(entryEntities, this::toSessionEntryDto),
                map(flagEntities, this::toFlagDto),
                map(questEntities, this::toQuestProgressionDto),
                map(campaignQuests, this::toQuestDto),
                map(clockEntities, this::toClockDto),
                map(frontEntities, this::toFrontDto));
    }

    // ----- Helpers de chargement -----

    private static <E, D> List<D> map(List<E> in, java.util.function.Function<E, D> f) {
        return in.stream().map(f).toList();
    }

    /** Liste 0/1 élément : l'entité d'id donné si présente. */
    private static <T, R extends org.springframework.data.repository.CrudRepository<T, Long>>
            List<T> singleton(R repo, Long id) {
        if (id == null) return List.of();
        return repo.findById(id).map(List::of).orElseGet(List::of);
    }

    private static Long parseLongOrNull(String s) {
        if (s == null || s.isBlank()) return null;
        try { return Long.parseLong(s.trim()); } catch (NumberFormatException e) { return null; }
    }

    private static void addLong(Set<Long> set, String idStr) {
        Long id = parseLongOrNull(idStr);
        if (id != null) set.add(id);
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
        // Les entités référencent les images par ID (cf. Image.getId() renvoyé à l'upload),
        // PAS par clé de stockage. On résout donc ID -> storageKey via l'index des images
        // exportées — même logique que collectReferencedFileStorageKeys pour les fichiers.
        java.util.Map<String, String> keyByImageId = new java.util.HashMap<>();
        for (ContentExport.ImageDto img : export.images()) {
            if (img.id() != null) keyByImageId.put(img.id().toString(), img.storageKey());
        }
        Set<String> refs = new LinkedHashSet<>();
        for (ContentExport.ArcDto a : export.arcs()) addAll(refs, a.illustrationImageIds());
        for (ContentExport.ChapterDto c : export.chapters()) addAll(refs, c.illustrationImageIds());
        if (export.quests() != null) {
            for (ContentExport.QuestDto q : export.quests()) addAll(refs, q.illustrationImageIds());
        }
        for (ContentExport.SceneDto s : export.scenes()) addAll(refs, s.illustrationImageIds());
        for (ContentExport.SceneDto s : export.scenes()) addRoomImageRefs(refs, s.rooms());
        for (ContentExport.CharacterDto c : export.characters()) {
            add(refs, c.portraitImageId());
            add(refs, c.headerImageId());
            addImageValues(refs, c.imageValues());
        }
        for (ContentExport.NpcDto n : export.npcs()) {
            add(refs, n.portraitImageId());
            add(refs, n.headerImageId());
            addImageValues(refs, n.imageValues());
        }
        for (ContentExport.EnemyDto e : export.enemies()) {
            add(refs, e.portraitImageId());
            add(refs, e.headerImageId());
            addImageValues(refs, e.imageValues());
        }
        for (ContentExport.PageDto p : export.pages()) addImageValues(refs, p.imageValues());

        Set<String> keys = new LinkedHashSet<>();
        for (String ref : refs) {
            String key = keyByImageId.get(ref);
            if (key != null && !key.isBlank()) keys.add(key);
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
            if (s.battlemaps() != null) {
                s.battlemaps().forEach(bm -> {
                    addFileKey(keys, keyById, bm.mediaFileId());
                    addFileKey(keys, keyById, bm.dataFileId());
                });
            }
            // Legacy (paire unique) : jamais renseigne sur les nouveaux exports,
            // mais ce collecteur sert aussi de reference au format on-disk.
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

    /** Réfs d'images portées par les salles (Room) d'une scène : galerie + plan. */
    private void addRoomImageRefs(Set<String> keys, List<Room> rooms) {
        if (rooms == null) return;
        for (Room r : rooms) {
            addAll(keys, r.getIllustrationImageIds());
            add(keys, r.getMapImageId());
        }
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
                e.getImageFraming(), e.getKeyValueValues(), e.getTableValues(), e.getNotes(),
                e.getTags(), e.getRelatedPageIds());
    }

    private ContentExport.CampaignDto toCampaignDto(CampaignJpaEntity e) {
        return new ContentExport.CampaignDto(e.getId(), e.getName(), e.getDescription(),
                e.getArcsCount(), e.getPlayerCount(), e.getLoreId(), e.getGameSystemId());
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
        // prerequisitesJson : champ conservé dans le bundle pour la rétro-compat de l'import
        // legacy (un vieux backup HUB→quête le lit). Les chapitres n'ont plus de prérequis -> "[]".
        return new ContentExport.ChapterDto(e.getId(), e.getName(), e.getDescription(),
                e.getArcId(), e.getOrder(), "[]", e.getIcon(),
                e.getGmNotes(), e.getPlayerObjectives(), e.getNarrativeStakes(),
                e.getRelatedPageIds(), e.getIllustrationImageIds());
    }

    private ContentExport.QuestDto toQuestDto(QuestJpaEntity e) {
        return new ContentExport.QuestDto(
                e.getId(), e.getCampaignId(), e.getArcId(), e.getName(), e.getDescription(), e.getIcon(), e.getOrder(),
                PREREQ_CONVERTER.convertToDatabaseColumn(e.getPrerequisites()),
                NODE_CONVERTER.convertToDatabaseColumn(e.getNodes()),
                e.getGmNotes(), e.getPlayerObjectives(), e.getNarrativeStakes(),
                e.getRelatedPageIds(), e.getIllustrationImageIds());
    }

    private ContentExport.SceneDto toSceneDto(SceneJpaEntity e) {
        return new ContentExport.SceneDto(e.getId(), e.getName(), e.getDescription(),
                e.getChapterId(), e.getOrder(), e.getIcon(), e.getLocation(),
                e.getTiming(), e.getAtmosphere(), e.getPlayerNarration(),
                e.getGmSecretNotes(), e.getChoicesConsequences(), e.getCombatDifficulty(),
                e.getEnemies(), e.getEnemyIds(), e.getRelatedPageIds(),
                e.getIllustrationImageIds(), null, null, // legacy battlemap : plus émis
                e.getBattlemaps(), e.getBranches(), e.getRooms(), e.getType(),
                e.getGraphX(), e.getGraphY());
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

    // ----- Mappers espace de jeu -----

    private ContentExport.PlaythroughDto toPlaythroughDto(PlaythroughJpaEntity e) {
        return new ContentExport.PlaythroughDto(e.getId(), e.getCampaignId(), e.getName(), e.getDescription());
    }

    private ContentExport.ClockDto toClockDto(ClockJpaEntity e) {
        return new ContentExport.ClockDto(e.getId(), e.getPlaythroughId(), e.getName(),
                e.getDescription(), e.getSegments(), e.getFilled(), e.getOrder(),
                e.getTriggerType(), e.getTriggerRef(), e.getFrontId());
    }

    private ContentExport.FrontDto toFrontDto(FrontJpaEntity e) {
        return new ContentExport.FrontDto(e.getId(), e.getPlaythroughId(), e.getName(),
                e.getDescription(), e.getOrder());
    }

    private ContentExport.SessionDto toSessionDto(SessionJpaEntity e) {
        return new ContentExport.SessionDto(e.getId(), e.getName(), e.getCampaignId(), e.getPlaythroughId(),
                e.getStartedAt() != null ? e.getStartedAt().toString() : null,
                e.getEndedAt() != null ? e.getEndedAt().toString() : null);
    }

    private ContentExport.SessionEntryDto toSessionEntryDto(SessionEntryJpaEntity e) {
        return new ContentExport.SessionEntryDto(e.getId(), e.getSessionId(),
                e.getType() != null ? e.getType().name() : null, e.getContent(),
                e.getOccurredAt() != null ? e.getOccurredAt().toString() : null);
    }

    private ContentExport.PlaythroughFlagDto toFlagDto(PlaythroughFlagJpaEntity e) {
        return new ContentExport.PlaythroughFlagDto(e.getId(), e.getPlaythroughId(), e.getName(), e.isValue());
    }

    private ContentExport.QuestProgressionDto toQuestProgressionDto(QuestProgressionJpaEntity e) {
        // Le champ DTO se nomme encore chapterId (format bundle v1) ; il porte désormais
        // le quest id (== chapter id partagé). Le renommage du format est traité en Phase 5.
        return new ContentExport.QuestProgressionDto(e.getId(), e.getPlaythroughId(), e.getQuestId(),
                e.getStatus() != null ? e.getStatus().name() : null);
    }
}
