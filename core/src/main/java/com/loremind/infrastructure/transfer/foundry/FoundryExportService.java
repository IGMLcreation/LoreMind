package com.loremind.infrastructure.transfer.foundry;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.loremind.domain.campaigncontext.structure.Room;
import com.loremind.domain.campaigncontext.structure.RoomBranch;
import com.loremind.domain.campaigncontext.structure.SceneBattlemap;
import com.loremind.domain.campaigncontext.structure.SceneBranch;
import com.loremind.domain.shared.template.FieldType;
import com.loremind.domain.shared.template.TemplateField;
import com.loremind.infrastructure.persistence.entity.*;
import com.loremind.infrastructure.persistence.jpa.*;
import com.loremind.domain.files.ports.FileStorage;
import com.loremind.domain.images.ports.ImageStorage;
import org.springframework.boot.info.BuildProperties;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;

import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.UncheckedIOException;
import java.util.*;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

/**
 * Construit le bundle d'export Foundry d'UNE campagne (cf.
 * {@code docs/foundry-bundle-schema.md}) : {@code manifest.json} + {@code data.json}
 * + {@code assets/} (images + battlemaps). Volontairement decouple de Foundry :
 * le bundle decrit les entites LoreMind, le module Foundry fait le mapping.
 */
@Service
public class FoundryExportService {

    private static final String FORMAT_VERSION = "1.0";
    private static final String CONTENT_FORMAT = "plain"; // textarea, ni markdown ni HTML

    private final CampaignJpaRepository campaignRepo;
    private final ArcJpaRepository arcRepo;
    private final ChapterJpaRepository chapterRepo;
    private final SceneJpaRepository sceneRepo;
    private final NpcJpaRepository npcRepo;
    private final EnemyJpaRepository enemyRepo;
    private final RandomTableJpaRepository randomTableRepo;
    private final GameSystemJpaRepository gameSystemRepo;
    private final ImageJpaRepository imageRepo;
    private final StoredFileJpaRepository storedFileRepo;
    private final ImageStorage imageStorage;
    private final FileStorage fileStorage;
    private final ObjectMapper objectMapper;
    private final String appVersion;

    public FoundryExportService(CampaignJpaRepository campaignRepo,
                                ArcJpaRepository arcRepo,
                                ChapterJpaRepository chapterRepo,
                                SceneJpaRepository sceneRepo,
                                NpcJpaRepository npcRepo,
                                EnemyJpaRepository enemyRepo,
                                RandomTableJpaRepository randomTableRepo,
                                GameSystemJpaRepository gameSystemRepo,
                                ImageJpaRepository imageRepo,
                                StoredFileJpaRepository storedFileRepo,
                                ImageStorage imageStorage,
                                FileStorage fileStorage,
                                ObjectMapper objectMapper,
                                @Nullable BuildProperties buildProperties) {
        this.campaignRepo = campaignRepo;
        this.arcRepo = arcRepo;
        this.chapterRepo = chapterRepo;
        this.sceneRepo = sceneRepo;
        this.npcRepo = npcRepo;
        this.enemyRepo = enemyRepo;
        this.randomTableRepo = randomTableRepo;
        this.gameSystemRepo = gameSystemRepo;
        this.imageRepo = imageRepo;
        this.storedFileRepo = storedFileRepo;
        this.imageStorage = imageStorage;
        this.fileStorage = fileStorage;
        this.objectMapper = objectMapper;
        this.appVersion = buildProperties != null ? buildProperties.getVersion() : "dev";
    }

    /** Bundle assemble (metadonnees uniquement ; les binaires sont streames au writeZip). */
    public record BuiltBundle(FoundryBundle.Manifest manifest,
                              FoundryBundle.Data data,
                              List<BinaryRef> binaries) {}

    /** Reference d'un binaire a copier dans le zip : chemin cible + cle de stockage. */
    public record BinaryRef(String path, String storageKey, boolean image) {}

    /**
     * Perimetre de l'export, choisi par l'utilisateur dans la modale :
     * - maps     : Scenes Foundry (battlemaps) + acteurs/tokens des ennemis lies.
     * - journals : journaux narratifs (arcs, chapitres/quetes, scenes, PNJ, bestiaire)
     *              avec leurs illustrations.
     * - tables   : RollTables.
     */
    public record ExportOptions(boolean maps, boolean journals, boolean tables) {
        public static ExportOptions all() { return new ExportOptions(true, true, true); }
    }

    /** Bundle complet (tout le perimetre) — conserve pour les appels existants. */
    public BuiltBundle buildBundle(String campaignId, String exportedAt) {
        return buildBundle(campaignId, exportedAt, ExportOptions.all());
    }

    /**
     * Assemble le bundle d'une campagne (sans toucher aux binaires : peu couteux).
     * Le perimetre exclu n'est PAS embarque (ni entites ni binaires) : un export
     * « cartes + ennemis » reste leger meme sur une campagne tres illustree.
     *
     * @throws NoSuchElementException si la campagne n'existe pas
     */
    public BuiltBundle buildBundle(String campaignId, String exportedAt, ExportOptions opts) {
        CampaignJpaEntity campaign = campaignRepo.findById(Long.parseLong(campaignId))
                .orElseThrow(() -> new NoSuchElementException("Campagne introuvable : " + campaignId));

        GameSystemJpaEntity gameSystem = resolveGameSystem(campaign.getGameSystemId());
        List<TemplateField> npcTemplate = gameSystem != null ? gameSystem.getNpcTemplate() : null;
        List<TemplateField> enemyTemplate = gameSystem != null ? gameSystem.getEnemyTemplate() : null;
        String foundryActorType = gameSystem != null ? gameSystem.getFoundryActorType() : null;

        AssetRegistry assets = new AssetRegistry();

        ArcsQuestsScenes structure = buildArcsQuestsScenes(campaign, opts, assets);
        List<FoundryBundle.Persona> npcs = buildNpcs(campaign, opts, npcTemplate, assets);
        List<FoundryBundle.Persona> enemies = buildEnemies(campaign, opts, enemyTemplate, foundryActorType, assets);
        List<FoundryBundle.RandomTable> randomTables = buildRandomTables(campaign, opts);

        FoundryBundle.Campaign campaignNode = new FoundryBundle.Campaign(
                str(campaign.getId()), campaign.getName(), campaign.getDescription(), campaign.getGameSystemId());

        FoundryBundle.Data data = new FoundryBundle.Data(
                FORMAT_VERSION, campaignNode,
                new FoundryBundle.Options(opts.maps(), opts.journals(), opts.tables()),
                structure.arcs(), structure.quests(), structure.scenes(),
                npcs, enemies, randomTables, assets.assets());

        FoundryBundle.Manifest manifest =
                buildManifest(campaign, exportedAt, structure, npcs, enemies, randomTables, assets);

        return new BuiltBundle(manifest, data, assets.binaries());
    }

    /** Arcs -> Quetes -> Scenes, a plat + refs parent (regroupes : construits par la meme triple boucle). */
    private record ArcsQuestsScenes(
            List<FoundryBundle.Arc> arcs, List<FoundryBundle.Quest> quests, List<FoundryBundle.Scene> scenes) {}

    /** Arcs -> Quetes -> Scenes (a plat + refs parent, tries par order). */
    private ArcsQuestsScenes buildArcsQuestsScenes(CampaignJpaEntity campaign, ExportOptions opts, AssetRegistry assets) {
        List<FoundryBundle.Arc> arcs = new ArrayList<>();
        List<FoundryBundle.Quest> quests = new ArrayList<>();
        List<FoundryBundle.Scene> scenes = new ArrayList<>();

        for (ArcJpaEntity arc : sortByOrder(arcRepo.findByCampaignId(campaign.getId()), ArcJpaEntity::getOrder)) {
            // Sans journaux, arcs/quetes ne servent que d'ossature (dossiers des Scenes) :
            // leurs illustrations ne sont pas embarquees.
            arcs.add(new FoundryBundle.Arc(
                    str(arc.getId()), arc.getName(), arc.getDescription(), arc.getOrder(),
                    arc.getType() != null ? arc.getType().name() : null, arc.getIcon(),
                    arc.getThemes(), arc.getStakes(), arc.getGmNotes(), arc.getRewards(), arc.getResolution(),
                    opts.journals() ? assets.images(arc.getIllustrationImageIds()) : List.of()));

            for (ChapterJpaEntity ch : sortByOrder(chapterRepo.findByArcId(arc.getId()), ChapterJpaEntity::getOrder)) {
                quests.add(new FoundryBundle.Quest(
                        str(ch.getId()), str(arc.getId()), ch.getName(), ch.getDescription(), ch.getOrder(),
                        ch.getIcon(), ch.getPlayerObjectives(), ch.getNarrativeStakes(), ch.getGmNotes(),
                        List.of(), opts.journals() ? assets.images(ch.getIllustrationImageIds()) : List.of()));

                for (SceneJpaEntity sc : sortByOrder(sceneRepo.findByChapterId(ch.getId()), SceneJpaEntity::getOrder)) {
                    scenes.add(toScene(sc, str(ch.getId()), assets, opts));
                }
            }
        }
        return new ArcsQuestsScenes(arcs, quests, scenes);
    }

    /** PNJ : purement journal — hors perimetre sans les journaux. */
    private List<FoundryBundle.Persona> buildNpcs(CampaignJpaEntity campaign, ExportOptions opts,
                                                  List<TemplateField> npcTemplate, AssetRegistry assets) {
        List<FoundryBundle.Persona> npcs = new ArrayList<>();
        if (!opts.journals()) return npcs;
        for (NpcJpaEntity n : npcRepo.findByCampaignIdOrderByOrderAsc(campaign.getId())) {
            npcs.add(new FoundryBundle.Persona(
                    str(n.getId()), n.getName(), n.getFolder(), n.getOrder(),
                    assets.image(n.getPortraitImageId()), assets.image(n.getHeaderImageId()), null, null, null,
                    fields(npcTemplate, n.getValues(), n.getKeyValueValues(), n.getImageValues(), assets)));
        }
        return npcs;
    }

    /**
     * Ennemis : necessaires aux cartes (acteurs/tokens, portrait = image du token)
     * ET aux journaux (bestiaire). Les galeries d'images des champs ne servent que
     * les journaux -> non embarquees en mode cartes seules.
     */
    private List<FoundryBundle.Persona> buildEnemies(CampaignJpaEntity campaign, ExportOptions opts,
                                                     List<TemplateField> enemyTemplate, String foundryActorType,
                                                     AssetRegistry assets) {
        List<FoundryBundle.Persona> enemies = new ArrayList<>();
        if (!opts.maps() && !opts.journals()) return enemies;
        for (EnemyJpaEntity e : enemyRepo.findByCampaignIdOrderByOrderAsc(campaign.getId())) {
            enemies.add(new FoundryBundle.Persona(
                    str(e.getId()), e.getName(), e.getFolder(), e.getOrder(),
                    assets.image(e.getPortraitImageId()),
                    opts.journals() ? assets.image(e.getHeaderImageId()) : null,
                    e.getLevel(),
                    e.getFoundryRef(),
                    buildFoundryActor(e, enemyTemplate, foundryActorType),
                    fields(enemyTemplate, e.getValues(), e.getKeyValueValues(),
                            opts.journals() ? e.getImageValues() : null, assets)));
        }
        return enemies;
    }

    private List<FoundryBundle.RandomTable> buildRandomTables(CampaignJpaEntity campaign, ExportOptions opts) {
        List<FoundryBundle.RandomTable> randomTables = new ArrayList<>();
        if (!opts.tables()) return randomTables;
        for (RandomTableJpaEntity t : randomTableRepo.findByCampaignIdOrderByOrderAsc(campaign.getId())) {
            List<FoundryBundle.RandomTableEntry> entries = new ArrayList<>();
            if (t.getEntries() != null) {
                for (RandomTableEntryJpaEntity en : t.getEntries()) {
                    entries.add(new FoundryBundle.RandomTableEntry(
                            en.getMinRoll(), en.getMaxRoll(), en.getLabel(), en.getDetail()));
                }
            }
            randomTables.add(new FoundryBundle.RandomTable(
                    str(t.getId()), t.getName(), t.getDescription(), t.getDiceFormula(), entries));
        }
        return randomTables;
    }

    private FoundryBundle.Manifest buildManifest(CampaignJpaEntity campaign, String exportedAt,
                                                 ArcsQuestsScenes structure, List<FoundryBundle.Persona> npcs,
                                                 List<FoundryBundle.Persona> enemies,
                                                 List<FoundryBundle.RandomTable> randomTables, AssetRegistry assets) {
        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("arcs", structure.arcs().size());
        counts.put("quests", structure.quests().size());
        counts.put("scenes", structure.scenes().size());
        counts.put("npcs", npcs.size());
        counts.put("enemies", enemies.size());
        counts.put("randomTables", randomTables.size());
        counts.put("assets", assets.assets().size());

        return new FoundryBundle.Manifest(
                FORMAT_VERSION, "loremind", appVersion, exportedAt,
                str(campaign.getId()), campaign.getName(), CONTENT_FORMAT, counts);
    }

    /** Serialise le bundle dans le flux au format .zip (binaires streames a la volee). */
    public void writeZip(BuiltBundle bundle, OutputStream out) {
        ObjectMapper writer = objectMapper.copy()
                .setDefaultPropertyInclusion(JsonInclude.Include.NON_NULL)
                .enable(SerializationFeature.INDENT_OUTPUT);
        try (ZipOutputStream zip = new ZipOutputStream(out)) {
            zip.putNextEntry(new ZipEntry("manifest.json"));
            zip.write(writer.writeValueAsBytes(bundle.manifest()));
            zip.closeEntry();

            zip.putNextEntry(new ZipEntry("data.json"));
            zip.write(writer.writeValueAsBytes(bundle.data()));
            zip.closeEntry();

            writeBinaries(zip, bundle.binaries());
        } catch (IOException e) {
            throw new UncheckedIOException("Echec de la generation du bundle Foundry", e);
        }
    }

    /** Ecrit chaque binaire reference, dedup par chemin cible (cle orpheline ignoree). */
    private void writeBinaries(ZipOutputStream zip, List<BinaryRef> binaries) throws IOException {
        Set<String> written = new HashSet<>();
        for (BinaryRef ref : binaries) {
            if (written.add(ref.path())) {
                writeBinaryEntry(zip, ref);
            }
        }
    }

    private void writeBinaryEntry(ZipOutputStream zip, BinaryRef ref) throws IOException {
        InputStream data = ref.image()
                ? imageStorage.download(ref.storageKey())
                : fileStorage.download(ref.storageKey());
        if (data == null) {
            return; // cle orpheline : on ignore
        }
        try (data) {
            zip.putNextEntry(new ZipEntry(ref.path()));
            data.transferTo(zip);
            zip.closeEntry();
        }
    }

    // ----- Mapping Scene -----

    private FoundryBundle.Scene toScene(SceneJpaEntity sc, String questId, AssetRegistry assets, ExportOptions opts) {
        List<FoundryBundle.LabeledBattlemap> battlemaps = opts.maps()
                ? battlemaps(sc.getBattlemaps(), assets)
                : List.of();
        // Champ legacy `battlemap` (première carte) conservé pour les modules Foundry existants.
        FoundryBundle.Battlemap battlemap = battlemaps.isEmpty() ? null
                : new FoundryBundle.Battlemap(battlemaps.get(0).mediaAssetId(), battlemaps.get(0).dataAssetId());
        return new FoundryBundle.Scene(
                str(sc.getId()), questId, sc.getName(), sc.getDescription(), sc.getOrder(), sc.getIcon(),
                sc.getLocation(), sc.getTiming(), sc.getAtmosphere(),
                sc.getPlayerNarration(), sc.getGmSecretNotes(), sc.getChoicesConsequences(),
                sc.getCombatDifficulty(), sc.getEnemies(), copy(sc.getEnemyIds()),
                opts.journals() ? assets.images(sc.getIllustrationImageIds()) : List.of(),
                battlemap, battlemaps,
                branches(sc.getBranches()), rooms(sc.getRooms(), assets, opts));
    }

    private List<FoundryBundle.LabeledBattlemap> battlemaps(List<SceneBattlemap> maps, AssetRegistry assets) {
        if (maps == null) return List.of();
        List<FoundryBundle.LabeledBattlemap> out = new ArrayList<>();
        for (SceneBattlemap bm : maps) {
            String media = assets.file(bm.mediaFileId(), "battlemapMedia");
            String data = assets.file(bm.dataFileId(), "battlemapData");
            if (media == null && data == null) continue;
            out.add(new FoundryBundle.LabeledBattlemap(bm.label(), media, data));
        }
        return out;
    }

    private List<FoundryBundle.Branch> branches(List<SceneBranch> branches) {
        if (branches == null) return List.of();
        List<FoundryBundle.Branch> out = new ArrayList<>();
        for (SceneBranch b : branches) out.add(new FoundryBundle.Branch(b.label(), b.targetSceneId(), b.condition()));
        return out;
    }

    private List<FoundryBundle.Room> rooms(List<Room> rooms, AssetRegistry assets, ExportOptions opts) {
        if (rooms == null) return List.of();
        List<FoundryBundle.Room> out = new ArrayList<>();
        for (Room r : rooms) {
            List<FoundryBundle.RoomBranch> rb = new ArrayList<>();
            if (r.getBranches() != null) {
                for (RoomBranch b : r.getBranches()) rb.add(new FoundryBundle.RoomBranch(b.label(), b.targetRoomId(), b.condition()));
            }
            out.add(new FoundryBundle.Room(
                    r.getId(), r.getName(), r.getDescription(), r.getEnemies(), copy(r.getEnemyIds()),
                    r.getLoot(), r.getTraps(), r.getGmNotes(), r.getFloor(), r.getOrder(),
                    opts.journals() ? assets.images(r.getIllustrationImageIds()) : List.of(), null, rb));
        }
        return out;
    }

    // ----- Resolution des champs PNJ/Ennemi via le template -----

    /** Résout le GameSystem UNE fois (templates PNJ/ennemi + type d'acteur en sont tirés). */
    private GameSystemJpaEntity resolveGameSystem(String gameSystemId) {
        if (gameSystemId == null || gameSystemId.isBlank()) return null;
        try {
            return gameSystemRepo.findById(Long.parseLong(gameSystemId)).orElse(null);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    /**
     * Construit l'acteur Foundry typé d'un ennemi MAISON (sans référence) : pose
     * {@code system.<foundryPath> = valeur} pour chaque champ mappé non vide. Null si
     * pas de type d'acteur, pas de champ mappé, ou si l'ennemi a déjà une référence
     * (auquel cas le vrai acteur du compendium est utilisé à la place).
     */
    private FoundryBundle.FoundryActor buildFoundryActor(EnemyJpaEntity e, List<TemplateField> template, String actorType) {
        if (actorType == null || actorType.isBlank() || template == null) return null;
        if (e.getFoundryRef() != null && !e.getFoundryRef().isBlank()) return null;

        Map<String, Object> system = new LinkedHashMap<>();
        Map<String, String> values = e.getValues();
        boolean any = false;
        for (TemplateField f : template) {
            FieldContribution c = foundryFieldContribution(f, values);
            if (c != null) {
                setPath(system, c.path(), c.value());
                any = true;
            }
        }
        return any ? new FoundryBundle.FoundryActor(actorType, system) : null;
    }

    /** Chemin Foundry ("a.b.c") + valeur d'un champ template pour {@link #buildFoundryActor}. */
    private record FieldContribution(String path, Object value) {}

    /** Contribution d'un champ, ou null si non mappe/sans valeur (nom, chemin Foundry ou valeur absent). */
    private static FieldContribution foundryFieldContribution(TemplateField f, Map<String, String> values) {
        if (f == null || f.getName() == null) return null;
        String path = f.getFoundryPath();
        if (path == null || path.isBlank()) return null;
        String raw = values != null ? values.get(f.getName()) : null;
        if (raw == null || raw.isBlank()) return null;
        return new FieldContribution(path, f.getType() == FieldType.NUMBER ? parseNumber(raw) : raw);
    }

    /** Pose une valeur dans un objet imbriqué selon un chemin pointé ("a.b.c"). */
    @SuppressWarnings("unchecked")
    private static void setPath(Map<String, Object> root, String path, Object value) {
        String[] parts = path.split("\\.");
        Map<String, Object> cur = root;
        for (int i = 0; i < parts.length - 1; i++) {
            Object next = cur.get(parts[i]);
            if (!(next instanceof Map)) {
                next = new LinkedHashMap<String, Object>();
                cur.put(parts[i], next);
            }
            cur = (Map<String, Object>) next;
        }
        cur.put(parts[parts.length - 1], value);
    }

    /** Convertit en Integer ou Double si possible (pour que Foundry reçoive un nombre). */
    private static Object parseNumber(String s) {
        String t = s.trim();
        try { return Integer.valueOf(t); } catch (NumberFormatException ignored) { /* fallthrough */ }
        try { return Double.valueOf(t.replace(',', '.')); } catch (NumberFormatException ignored) { /* fallthrough */ }
        return s;
    }

    private List<FoundryBundle.Field> fields(List<TemplateField> template,
                                             Map<String, String> values,
                                             Map<String, Map<String, String>> keyValueValues,
                                             Map<String, List<String>> imageValues,
                                             AssetRegistry assets) {
        if (template == null || template.isEmpty()) {
            return rawFields(values);
        }
        List<FoundryBundle.Field> out = new ArrayList<>();
        for (TemplateField f : template) {
            FoundryBundle.Field field = toField(f, values, keyValueValues, imageValues, assets);
            if (field != null) {
                out.add(field);
            }
        }
        return out;
    }

    /** Repli sans template : paires brutes label=cle (valeurs non vides uniquement). */
    private static List<FoundryBundle.Field> rawFields(Map<String, String> values) {
        List<FoundryBundle.Field> out = new ArrayList<>();
        if (values != null) {
            for (Map.Entry<String, String> e : values.entrySet()) {
                if (e.getValue() != null && !e.getValue().isBlank()) {
                    out.add(new FoundryBundle.Field("text", e.getKey(), e.getValue(), null, null));
                }
            }
        }
        return out;
    }

    /** Un champ Foundry pour ce TemplateField (dispatch par type), ou null si non mappe/non applicable/vide. */
    private static FoundryBundle.Field toField(TemplateField f,
                                               Map<String, String> values,
                                               Map<String, Map<String, String>> keyValueValues,
                                               Map<String, List<String>> imageValues,
                                               AssetRegistry assets) {
        if (f == null || f.getName() == null || f.getType() == null) return null;
        return switch (f.getType()) {
            case TEXT, NUMBER -> textOrNumberField(f, values);
            case KEY_VALUE_LIST -> keyValueField(f, keyValueValues);
            case IMAGE -> imageField(f, imageValues, assets);
            case TABLE -> null; // pas de stockage cote PNJ/Ennemi -> ignore.
        };
    }

    private static FoundryBundle.Field textOrNumberField(TemplateField f, Map<String, String> values) {
        String v = values != null ? values.get(f.getName()) : null;
        if (v == null || v.isBlank()) return null;
        return new FoundryBundle.Field(f.getType() == FieldType.NUMBER ? "number" : "text", f.getName(), v, null, null);
    }

    private static FoundryBundle.Field keyValueField(TemplateField f, Map<String, Map<String, String>> keyValueValues) {
        Map<String, String> inner = keyValueValues != null ? keyValueValues.get(f.getName()) : null;
        List<String> labels = f.getLabels();
        if (inner == null || labels == null) return null;
        List<FoundryBundle.Entry> entries = new ArrayList<>();
        for (String label : labels) {
            String v = inner.get(label);
            if (v != null && !v.isBlank()) entries.add(new FoundryBundle.Entry(label, v));
        }
        return entries.isEmpty() ? null : new FoundryBundle.Field("keyValueList", f.getName(), null, entries, null);
    }

    private static FoundryBundle.Field imageField(TemplateField f, Map<String, List<String>> imageValues,
                                                  AssetRegistry assets) {
        List<String> ids = imageValues != null ? imageValues.get(f.getName()) : null;
        List<String> assetIds = assets.images(ids);
        return assetIds.isEmpty() ? null : new FoundryBundle.Field("image", f.getName(), null, null, assetIds);
    }

    // ----- Registre des assets (images + battlemaps), dedup + index -----

    private final class AssetRegistry {
        private final LinkedHashMap<String, FoundryBundle.Asset> byId = new LinkedHashMap<>();
        private final List<BinaryRef> binaries = new ArrayList<>();

        /** Enregistre une image par son ID LoreMind, retourne l'assetId ("img-<id>") ou null. */
        String image(String imageId) {
            if (imageId == null || imageId.isBlank()) return null;
            String assetId = "img-" + imageId;
            if (byId.containsKey(assetId)) return assetId;
            ImageJpaEntity e;
            try {
                e = imageRepo.findById(Long.parseLong(imageId)).orElse(null);
            } catch (NumberFormatException ex) {
                return null;
            }
            if (e == null) return null;
            String path = "assets/images/" + assetId + extOf(e.getStorageKey());
            byId.put(assetId, new FoundryBundle.Asset(assetId, "image", path,
                    e.getFilename(), e.getContentType(), e.getSizeBytes()));
            binaries.add(new BinaryRef(path, e.getStorageKey(), true));
            return assetId;
        }

        List<String> images(List<String> imageIds) {
            if (imageIds == null) return List.of();
            List<String> out = new ArrayList<>();
            for (String id : imageIds) {
                String a = image(id);
                if (a != null) out.add(a);
            }
            return out;
        }

        /** Enregistre un fichier (battlemap) par son ID, retourne l'assetId ("file-<id>") ou null. */
        String file(String fileId, String kind) {
            if (fileId == null || fileId.isBlank()) return null;
            String assetId = "file-" + fileId;
            if (byId.containsKey(assetId)) return assetId;
            StoredFileJpaEntity e;
            try {
                e = storedFileRepo.findById(Long.parseLong(fileId)).orElse(null);
            } catch (NumberFormatException ex) {
                return null;
            }
            if (e == null) return null;
            String path = "assets/battlemaps/" + assetId + extOf(e.getStorageKey());
            byId.put(assetId, new FoundryBundle.Asset(assetId, kind, path,
                    e.getFilename(), e.getContentType(), e.getSizeBytes()));
            binaries.add(new BinaryRef(path, e.getStorageKey(), false));
            return assetId;
        }

        List<FoundryBundle.Asset> assets() { return new ArrayList<>(byId.values()); }

        List<BinaryRef> binaries() { return binaries; }
    }

    // ----- Helpers -----

    private static <T> List<T> sortByOrder(List<T> list, java.util.function.ToIntFunction<T> order) {
        List<T> copy = new ArrayList<>(list);
        copy.sort(Comparator.comparingInt(order));
        return copy;
    }

    private static List<String> copy(List<String> list) {
        return list != null ? new ArrayList<>(list) : List.of();
    }

    private static String str(Long id) {
        return id != null ? id.toString() : null;
    }

    /** Extension (avec le point) extraite de la cle de stockage "prefix/UUID.ext". */
    private static String extOf(String storageKey) {
        if (storageKey == null) return "";
        int slash = storageKey.lastIndexOf('/');
        int dot = storageKey.lastIndexOf('.');
        return (dot >= 0 && dot > slash) ? storageKey.substring(dot) : "";
    }
}
