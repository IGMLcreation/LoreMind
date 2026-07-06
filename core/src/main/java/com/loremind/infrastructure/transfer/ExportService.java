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

        StructuralClosure structure = loadStructuralClosure(cid);
        // Système de jeu lié : TOUJOURS inclus (templates/PDF en dépendent).
        List<GameSystemJpaEntity> gsEntities = singleton(gameSystemRepo, parseLongOrNull(campaign.getGameSystemId()));
        LoreClosure lore = loadLoreClosure(campaign, req);
        PlayClosure play = loadPlayClosure(cid, req);
        // Quêtes de la campagne (Niveau 1) — toujours incluses dans la clôture.
        List<QuestJpaEntity> campaignQuests = questRepo.findByCampaignId(cid);
        // Images/fichiers : uniquement les binaires RÉFÉRENCÉS par la clôture (si option active).
        BinaryClosure binaries = req.includeImages()
                ? loadBinaryClosure(structure, lore, play, campaignQuests) : BinaryClosure.EMPTY;

        ContentExport.CampaignDto campaignDto = campaignDto(campaign, req.includeLore());

        ContentExport.Manifest manifest =
                new ContentExport.Manifest(FORMAT_VERSION, appVersion, exportedAt, campaign.getName());
        return new ContentExport(manifest,
                map(gsEntities, this::toGameSystemDto),
                map(lore.lores(), this::toLoreDto),
                map(lore.loreNodes(), this::toLoreNodeDto),
                map(lore.templates(), this::toTemplateDto),
                map(lore.pages(), this::toPageDto),
                List.of(campaignDto),
                map(structure.arcs(), this::toArcDto),
                map(structure.chapters(), this::toChapterDto),
                map(structure.scenes(), this::toSceneDto),
                map(play.characters(), this::toCharacterDto),
                map(structure.npcs(), this::toNpcDto),
                map(structure.enemies(), this::toEnemyDto),
                map(structure.catalogs(), this::toItemCatalogDto),
                map(structure.tables(), this::toRandomTableDto),
                map(binaries.images(), this::toImageDto),
                map(binaries.files(), this::toStoredFileDto),
                map(play.playthroughs(), this::toPlaythroughDto),
                map(play.sessions(), this::toSessionDto),
                map(play.entries(), this::toSessionEntryDto),
                map(play.flags(), this::toFlagDto),
                map(play.questProgressions(), this::toQuestProgressionDto),
                map(campaignQuests, this::toQuestDto),
                map(play.clocks(), this::toClockDto),
                map(play.fronts(), this::toFrontDto));
    }

    /** Clôture structurelle de la campagne : arcs -> chapitres -> scènes, PNJ, ennemis, catalogues, tables. */
    private record StructuralClosure(
            List<ArcJpaEntity> arcs, List<ChapterJpaEntity> chapters, List<SceneJpaEntity> scenes,
            List<NpcJpaEntity> npcs, List<EnemyJpaEntity> enemies,
            List<ItemCatalogJpaEntity> catalogs, List<RandomTableJpaEntity> tables) {}

    private StructuralClosure loadStructuralClosure(Long cid) {
        List<ArcJpaEntity> arcEntities = arcRepo.findByCampaignId(cid);
        List<ChapterJpaEntity> chapterEntities = arcEntities.stream()
                .flatMap(a -> chapterRepo.findByArcId(a.getId()).stream()).toList();
        List<SceneJpaEntity> sceneEntities = chapterEntities.stream()
                .flatMap(c -> sceneRepo.findByChapterId(c.getId()).stream()).toList();
        return new StructuralClosure(arcEntities, chapterEntities, sceneEntities,
                npcRepo.findByCampaignIdOrderByOrderAsc(cid), enemyRepo.findByCampaignIdOrderByOrderAsc(cid),
                itemCatalogRepo.findByCampaignIdOrderByOrderAsc(cid), randomTableRepo.findByCampaignIdOrderByOrderAsc(cid));
    }

    /** Univers (lore) lié à la campagne : optionnel selon {@code req.includeLore()}. */
    private record LoreClosure(
            List<LoreJpaEntity> lores, List<LoreNodeJpaEntity> loreNodes,
            List<TemplateJpaEntity> templates, List<PageJpaEntity> pages) {
        private static final LoreClosure EMPTY = new LoreClosure(List.of(), List.of(), List.of(), List.of());
    }

    private LoreClosure loadLoreClosure(CampaignJpaEntity campaign, ExportRequest req) {
        Long lid = req.includeLore() ? parseLongOrNull(campaign.getLoreId()) : null;
        if (lid == null) return LoreClosure.EMPTY;
        return new LoreClosure(singleton(loreRepo, lid), loreNodeRepo.findByLoreId(lid),
                templateRepo.findByLoreId(lid), pageRepo.findByLoreId(lid));
    }

    /**
     * Espace de jeu (parties -> séances/journal/flags/quêtes + feuilles de perso) : optionnel
     * selon {@code req.includePlay()}. Les feuilles de perso appartiennent à une Partie, donc
     * « sans jeu » = sans feuilles de perso.
     */
    private record PlayClosure(
            List<PlaythroughJpaEntity> playthroughs, List<SessionJpaEntity> sessions,
            List<SessionEntryJpaEntity> entries, List<PlaythroughFlagJpaEntity> flags,
            List<QuestProgressionJpaEntity> questProgressions, List<ClockJpaEntity> clocks,
            List<FrontJpaEntity> fronts, List<CharacterJpaEntity> characters) {
        private static final PlayClosure EMPTY = new PlayClosure(
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of());
    }

    private PlayClosure loadPlayClosure(Long cid, ExportRequest req) {
        if (!req.includePlay()) return PlayClosure.EMPTY;
        List<PlaythroughJpaEntity> ptEntities = playthroughRepo.findByCampaignId(cid);
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
        return new PlayClosure(ptEntities, sessionEntities, entryEntities, flagEntities,
                questEntities, clockEntities, frontEntities, characterEntities);
    }

    /** Binaires (images/fichiers) référencés par la clôture exportée. */
    private record BinaryClosure(List<ImageJpaEntity> images, List<StoredFileJpaEntity> files) {
        private static final BinaryClosure EMPTY = new BinaryClosure(List.of(), List.of());
    }

    private BinaryClosure loadBinaryClosure(StructuralClosure structure, LoreClosure lore, PlayClosure play,
                                            List<QuestJpaEntity> campaignQuests) {
        Set<String> imageRefs = new LinkedHashSet<>();
        structure.arcs().forEach(a -> addAll(imageRefs, a.getIllustrationImageIds()));
        structure.chapters().forEach(c -> addAll(imageRefs, c.getIllustrationImageIds()));
        campaignQuests.forEach(q -> addAll(imageRefs, q.getIllustrationImageIds()));
        structure.scenes().forEach(s -> addAll(imageRefs, s.getIllustrationImageIds()));
        structure.scenes().forEach(s -> addRoomImageRefs(imageRefs, s.getRooms()));
        structure.npcs().forEach(n -> { add(imageRefs, n.getPortraitImageId()); add(imageRefs, n.getHeaderImageId()); addImageValues(imageRefs, n.getImageValues()); });
        structure.enemies().forEach(e -> { add(imageRefs, e.getPortraitImageId()); add(imageRefs, e.getHeaderImageId()); addImageValues(imageRefs, e.getImageValues()); });
        play.characters().forEach(c -> { add(imageRefs, c.getPortraitImageId()); add(imageRefs, c.getHeaderImageId()); addImageValues(imageRefs, c.getImageValues()); });
        lore.pages().forEach(p -> addImageValues(imageRefs, p.getImageValues()));
        List<ImageJpaEntity> imageEntities = imageRefs.stream()
                .map(ExportService::parseLongOrNull).filter(java.util.Objects::nonNull)
                .map(id -> imageRepo.findById(id).orElse(null)).filter(java.util.Objects::nonNull)
                .distinct().toList();

        Set<Long> fileRefs = new LinkedHashSet<>();
        structure.scenes().forEach(s -> {
            if (s.getBattlemaps() == null) return;
            s.getBattlemaps().forEach(bm -> { addLong(fileRefs, bm.mediaFileId()); addLong(fileRefs, bm.dataFileId()); });
        });
        List<StoredFileJpaEntity> fileEntities = fileRefs.stream()
                .map(id -> storedFileRepo.findById(id).orElse(null)).filter(java.util.Objects::nonNull).toList();

        return new BinaryClosure(imageEntities, fileEntities);
    }

    /**
     * DTO Campaign : si le lore n'est pas exporté, on neutralise loreId (évite une
     * référence pendante vers un univers absent à l'import).
     */
    private ContentExport.CampaignDto campaignDto(CampaignJpaEntity campaign, boolean includeLore) {
        ContentExport.CampaignDto dto = toCampaignDto(campaign);
        if (includeLore) return dto;
        return new ContentExport.CampaignDto(dto.id(), dto.name(), dto.description(),
                dto.arcsCount(), dto.playerCount(), null, dto.gameSystemId());
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
            writeImageBinaries(zip, collectReferencedStorageKeys(export));

            // Binaires fichiers (battlemaps : media + sidecar) : ceux references par
            // les scenes. Stockes a part sous "files/<storageKey>".
            writeFileBinaries(zip, collectReferencedFileStorageKeys(export));
        } catch (IOException e) {
            throw new UncheckedIOException("Echec de la generation du zip d'export", e);
        }
    }

    /** Ecrit un binaire d'image par cle REFERENCEE (deduplique, cles orphelines/vides ignorees). */
    private void writeImageBinaries(ZipOutputStream zip, Set<String> referenced) throws IOException {
        Set<String> written = new LinkedHashSet<>();
        for (String key : referenced) {
            if (isWritable(key, written)) {
                writeImageEntry(zip, key);
            }
        }
    }

    private void writeImageEntry(ZipOutputStream zip, String key) throws IOException {
        try (InputStream data = imageStorage.download(key)) {
            if (data == null) {
                return; // cle orpheline : on ignore silencieusement
            }
            zip.putNextEntry(new ZipEntry("images/" + key));
            data.transferTo(zip);
            zip.closeEntry();
        }
    }

    /** Ecrit un binaire de fichier (battlemap) par cle REFERENCEE (deduplique, cles orphelines/vides ignorees). */
    private void writeFileBinaries(ZipOutputStream zip, Set<String> referenced) throws IOException {
        Set<String> written = new LinkedHashSet<>();
        for (String key : referenced) {
            if (isWritable(key, written)) {
                writeFileEntry(zip, key);
            }
        }
    }

    private void writeFileEntry(ZipOutputStream zip, String key) throws IOException {
        try (InputStream data = fileStorage.download(key)) {
            if (data == null) {
                return; // cle orpheline : on ignore silencieusement
            }
            zip.putNextEntry(new ZipEntry("files/" + key));
            data.transferTo(zip);
            zip.closeEntry();
        }
    }

    /** Vrai si la cle est ecrivable : non nulle/vide, et pas deja ecrite (marque au passage). */
    private static boolean isWritable(String key, Set<String> written) {
        return key != null && !key.isBlank() && written.add(key);
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
        java.util.Map<String, String> keyByImageId = indexImagesById(export);
        Set<String> refs = collectImageIdRefs(export);
        return resolveStorageKeys(refs, keyByImageId);
    }

    private static java.util.Map<String, String> indexImagesById(ContentExport export) {
        java.util.Map<String, String> keyByImageId = new java.util.HashMap<>();
        for (ContentExport.ImageDto img : export.images()) {
            if (img.id() != null) keyByImageId.put(img.id().toString(), img.storageKey());
        }
        return keyByImageId;
    }

    /** Collecte les IDs d'images référencés par toutes les entités exportées (avant résolution en storageKey). */
    private Set<String> collectImageIdRefs(ContentExport export) {
        Set<String> refs = new LinkedHashSet<>();
        for (ContentExport.ArcDto a : export.arcs()) addAll(refs, a.illustrationImageIds());
        for (ContentExport.ChapterDto c : export.chapters()) addAll(refs, c.illustrationImageIds());
        addQuestImageRefs(refs, export.quests());
        for (ContentExport.SceneDto s : export.scenes()) addAll(refs, s.illustrationImageIds());
        for (ContentExport.SceneDto s : export.scenes()) addRoomImageRefs(refs, s.rooms());
        for (ContentExport.CharacterDto c : export.characters()) addCharacterImageRefs(refs, c);
        for (ContentExport.NpcDto n : export.npcs()) addNpcImageRefs(refs, n);
        for (ContentExport.EnemyDto e : export.enemies()) addEnemyImageRefs(refs, e);
        for (ContentExport.PageDto p : export.pages()) addImageValues(refs, p.imageValues());
        return refs;
    }

    private void addQuestImageRefs(Set<String> refs, List<ContentExport.QuestDto> quests) {
        if (quests == null) return;
        for (ContentExport.QuestDto q : quests) addAll(refs, q.illustrationImageIds());
    }

    private void addCharacterImageRefs(Set<String> refs, ContentExport.CharacterDto c) {
        add(refs, c.portraitImageId());
        add(refs, c.headerImageId());
        addImageValues(refs, c.imageValues());
    }

    private void addNpcImageRefs(Set<String> refs, ContentExport.NpcDto n) {
        add(refs, n.portraitImageId());
        add(refs, n.headerImageId());
        addImageValues(refs, n.imageValues());
    }

    private void addEnemyImageRefs(Set<String> refs, ContentExport.EnemyDto e) {
        add(refs, e.portraitImageId());
        add(refs, e.headerImageId());
        addImageValues(refs, e.imageValues());
    }

    private static Set<String> resolveStorageKeys(Set<String> refs, java.util.Map<String, String> keyByImageId) {
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
