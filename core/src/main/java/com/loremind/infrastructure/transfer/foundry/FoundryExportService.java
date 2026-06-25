package com.loremind.infrastructure.transfer.foundry;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.SerializationFeature;
import com.loremind.domain.campaigncontext.Prerequisite;
import com.loremind.domain.campaigncontext.Room;
import com.loremind.domain.campaigncontext.RoomBranch;
import com.loremind.domain.campaigncontext.SceneBranch;
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
     * Assemble le bundle d'une campagne (sans toucher aux binaires : peu couteux).
     *
     * @throws NoSuchElementException si la campagne n'existe pas
     */
    public BuiltBundle buildBundle(String campaignId, String exportedAt) {
        CampaignJpaEntity campaign = campaignRepo.findById(Long.parseLong(campaignId))
                .orElseThrow(() -> new NoSuchElementException("Campagne introuvable : " + campaignId));

        List<TemplateField> npcTemplate = resolveTemplate(campaign.getGameSystemId(), true);
        List<TemplateField> enemyTemplate = resolveTemplate(campaign.getGameSystemId(), false);
        String foundryActorType = resolveActorType(campaign.getGameSystemId());

        AssetRegistry assets = new AssetRegistry();

        // Arcs -> Quetes -> Scenes (a plat + refs parent, tries par order).
        List<FoundryBundle.Arc> arcs = new ArrayList<>();
        List<FoundryBundle.Quest> quests = new ArrayList<>();
        List<FoundryBundle.Scene> scenes = new ArrayList<>();

        List<ArcJpaEntity> arcEntities = sortByOrder(arcRepo.findByCampaignId(campaign.getId()), ArcJpaEntity::getOrder);
        for (ArcJpaEntity arc : arcEntities) {
            arcs.add(new FoundryBundle.Arc(
                    str(arc.getId()), arc.getName(), arc.getDescription(), arc.getOrder(),
                    arc.getType() != null ? arc.getType().name() : null, arc.getIcon(),
                    arc.getThemes(), arc.getStakes(), arc.getGmNotes(), arc.getRewards(), arc.getResolution(),
                    assets.images(arc.getIllustrationImageIds())));

            for (ChapterJpaEntity ch : sortByOrder(chapterRepo.findByArcId(arc.getId()), ChapterJpaEntity::getOrder)) {
                quests.add(new FoundryBundle.Quest(
                        str(ch.getId()), str(arc.getId()), ch.getName(), ch.getDescription(), ch.getOrder(),
                        ch.getIcon(), ch.getPlayerObjectives(), ch.getNarrativeStakes(), ch.getGmNotes(),
                        prerequisites(ch.getPrerequisites()), assets.images(ch.getIllustrationImageIds())));

                for (SceneJpaEntity sc : sortByOrder(sceneRepo.findByChapterId(ch.getId()), SceneJpaEntity::getOrder)) {
                    scenes.add(toScene(sc, str(ch.getId()), assets));
                }
            }
        }

        List<FoundryBundle.Persona> npcs = new ArrayList<>();
        for (NpcJpaEntity n : npcRepo.findByCampaignIdOrderByOrderAsc(campaign.getId())) {
            npcs.add(new FoundryBundle.Persona(
                    str(n.getId()), n.getName(), n.getFolder(), n.getOrder(),
                    assets.image(n.getPortraitImageId()), assets.image(n.getHeaderImageId()), null, null, null,
                    fields(npcTemplate, n.getValues(), n.getKeyValueValues(), n.getImageValues(), assets)));
        }

        List<FoundryBundle.Persona> enemies = new ArrayList<>();
        for (EnemyJpaEntity e : enemyRepo.findByCampaignIdOrderByOrderAsc(campaign.getId())) {
            enemies.add(new FoundryBundle.Persona(
                    str(e.getId()), e.getName(), e.getFolder(), e.getOrder(),
                    assets.image(e.getPortraitImageId()), assets.image(e.getHeaderImageId()), e.getLevel(),
                    e.getFoundryRef(),
                    buildFoundryActor(e, enemyTemplate, foundryActorType),
                    fields(enemyTemplate, e.getValues(), e.getKeyValueValues(), e.getImageValues(), assets)));
        }

        List<FoundryBundle.RandomTable> randomTables = new ArrayList<>();
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

        FoundryBundle.Campaign campaignNode = new FoundryBundle.Campaign(
                str(campaign.getId()), campaign.getName(), campaign.getDescription(), campaign.getGameSystemId());

        FoundryBundle.Data data = new FoundryBundle.Data(
                FORMAT_VERSION, campaignNode, arcs, quests, scenes, npcs, enemies, randomTables, assets.assets());

        Map<String, Integer> counts = new LinkedHashMap<>();
        counts.put("arcs", arcs.size());
        counts.put("quests", quests.size());
        counts.put("scenes", scenes.size());
        counts.put("npcs", npcs.size());
        counts.put("enemies", enemies.size());
        counts.put("randomTables", randomTables.size());
        counts.put("assets", assets.assets().size());

        FoundryBundle.Manifest manifest = new FoundryBundle.Manifest(
                FORMAT_VERSION, "loremind", appVersion, exportedAt,
                str(campaign.getId()), campaign.getName(), CONTENT_FORMAT, counts);

        return new BuiltBundle(manifest, data, assets.binaries());
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

            Set<String> written = new HashSet<>();
            for (BinaryRef ref : bundle.binaries()) {
                if (!written.add(ref.path())) continue; // dedup defensif
                InputStream data = ref.image()
                        ? imageStorage.download(ref.storageKey())
                        : fileStorage.download(ref.storageKey());
                if (data == null) continue; // cle orpheline : on ignore
                try (data) {
                    zip.putNextEntry(new ZipEntry(ref.path()));
                    data.transferTo(zip);
                    zip.closeEntry();
                }
            }
        } catch (IOException e) {
            throw new UncheckedIOException("Echec de la generation du bundle Foundry", e);
        }
    }

    // ----- Mapping Scene -----

    private FoundryBundle.Scene toScene(SceneJpaEntity sc, String questId, AssetRegistry assets) {
        FoundryBundle.Battlemap battlemap = battlemap(sc.getBattlemapMediaFileId(), sc.getBattlemapDataFileId(), assets);
        return new FoundryBundle.Scene(
                str(sc.getId()), questId, sc.getName(), sc.getDescription(), sc.getOrder(), sc.getIcon(),
                sc.getLocation(), sc.getTiming(), sc.getAtmosphere(),
                sc.getPlayerNarration(), sc.getGmSecretNotes(), sc.getChoicesConsequences(),
                sc.getCombatDifficulty(), sc.getEnemies(), copy(sc.getEnemyIds()),
                assets.images(sc.getIllustrationImageIds()), battlemap,
                branches(sc.getBranches()), rooms(sc.getRooms(), assets));
    }

    private FoundryBundle.Battlemap battlemap(String mediaId, String dataId, AssetRegistry assets) {
        String media = assets.file(mediaId, "battlemapMedia");
        String data = assets.file(dataId, "battlemapData");
        if (media == null && data == null) return null;
        return new FoundryBundle.Battlemap(media, data);
    }

    private List<FoundryBundle.Branch> branches(List<SceneBranch> branches) {
        if (branches == null) return List.of();
        List<FoundryBundle.Branch> out = new ArrayList<>();
        for (SceneBranch b : branches) out.add(new FoundryBundle.Branch(b.label(), b.targetSceneId(), b.condition()));
        return out;
    }

    private List<FoundryBundle.Room> rooms(List<Room> rooms, AssetRegistry assets) {
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
                    assets.images(r.getIllustrationImageIds()), null, rb));
        }
        return out;
    }

    private List<Map<String, Object>> prerequisites(List<Prerequisite> prereqs) {
        if (prereqs == null || prereqs.isEmpty()) return List.of();
        List<Map<String, Object>> out = new ArrayList<>();
        for (Prerequisite p : prereqs) {
            if (p instanceof Prerequisite.QuestCompleted qc) {
                out.add(Map.of("type", "questCompleted", "questId", String.valueOf(qc.questId())));
            } else if (p instanceof Prerequisite.SessionReached sr) {
                out.add(Map.of("type", "sessionReached", "minSessionNumber", sr.minSessionNumber()));
            } else if (p instanceof Prerequisite.FlagSet fs) {
                out.add(Map.of("type", "flagSet", "flagName", fs.flagName()));
            }
        }
        return out;
    }

    // ----- Resolution des champs PNJ/Ennemi via le template -----

    private List<TemplateField> resolveTemplate(String gameSystemId, boolean npc) {
        if (gameSystemId == null || gameSystemId.isBlank()) return null;
        try {
            return gameSystemRepo.findById(Long.parseLong(gameSystemId))
                    .map(gs -> npc ? gs.getNpcTemplate() : gs.getEnemyTemplate())
                    .orElse(null);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private String resolveActorType(String gameSystemId) {
        if (gameSystemId == null || gameSystemId.isBlank()) return null;
        try {
            return gameSystemRepo.findById(Long.parseLong(gameSystemId))
                    .map(GameSystemJpaEntity::getFoundryActorType)
                    .orElse(null);
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
            if (f == null || f.getName() == null) continue;
            String path = f.getFoundryPath();
            if (path == null || path.isBlank()) continue;
            String raw = values != null ? values.get(f.getName()) : null;
            if (raw == null || raw.isBlank()) continue;
            setPath(system, path, f.getType() == FieldType.NUMBER ? parseNumber(raw) : raw);
            any = true;
        }
        return any ? new FoundryBundle.FoundryActor(actorType, system) : null;
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
        List<FoundryBundle.Field> out = new ArrayList<>();
        // Repli : pas de template -> paires brutes label=cle.
        if (template == null || template.isEmpty()) {
            if (values != null) {
                for (Map.Entry<String, String> e : values.entrySet()) {
                    if (e.getValue() != null && !e.getValue().isBlank()) {
                        out.add(new FoundryBundle.Field("text", e.getKey(), e.getValue(), null, null));
                    }
                }
            }
            return out;
        }
        for (TemplateField f : template) {
            if (f == null || f.getName() == null || f.getType() == null) continue;
            FieldType type = f.getType();
            if (type == FieldType.TEXT || type == FieldType.NUMBER) {
                String v = values != null ? values.get(f.getName()) : null;
                if (v != null && !v.isBlank()) {
                    out.add(new FoundryBundle.Field(type == FieldType.NUMBER ? "number" : "text",
                            f.getName(), v, null, null));
                }
            } else if (type == FieldType.KEY_VALUE_LIST) {
                Map<String, String> inner = keyValueValues != null ? keyValueValues.get(f.getName()) : null;
                List<String> labels = f.getLabels();
                if (inner != null && labels != null) {
                    List<FoundryBundle.Entry> entries = new ArrayList<>();
                    for (String label : labels) {
                        String v = inner.get(label);
                        if (v != null && !v.isBlank()) entries.add(new FoundryBundle.Entry(label, v));
                    }
                    if (!entries.isEmpty()) {
                        out.add(new FoundryBundle.Field("keyValueList", f.getName(), null, entries, null));
                    }
                }
            } else if (type == FieldType.IMAGE) {
                List<String> ids = imageValues != null ? imageValues.get(f.getName()) : null;
                List<String> assetIds = assets.images(ids);
                if (!assetIds.isEmpty()) {
                    out.add(new FoundryBundle.Field("image", f.getName(), null, null, assetIds));
                }
            }
            // TABLE : pas de stockage cote PNJ/Ennemi -> ignore.
        }
        return out;
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
