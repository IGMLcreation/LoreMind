package com.loremind.infrastructure.transfer.foundry;

import com.loremind.domain.campaigncontext.structure.SceneBattlemap;
import com.loremind.domain.shared.template.TemplateField;
import com.loremind.infrastructure.persistence.entity.*;
import com.loremind.infrastructure.persistence.jpa.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test d'integration de l'assemblage du bundle Foundry (metadonnees uniquement :
 * {@code buildBundle} ne touche pas aux binaires, donc pas besoin de MinIO).
 * Couvre : hierarchie arc/quete/scene, battlemap (media+sidecar), assets (image +
 * fichiers), et resolution des champs PNJ via le template du GameSystem.
 */
@SpringBootTest
@Transactional
class FoundryExportServiceTest {

    @Autowired private FoundryExportService service;
    @Autowired private CampaignJpaRepository campaignRepo;
    @Autowired private ArcJpaRepository arcRepo;
    @Autowired private ChapterJpaRepository chapterRepo;
    @Autowired private SceneJpaRepository sceneRepo;
    @Autowired private NpcJpaRepository npcRepo;
    @Autowired private GameSystemJpaRepository gameSystemRepo;
    @Autowired private ImageJpaRepository imageRepo;
    @Autowired private StoredFileJpaRepository fileRepo;
    @Autowired private RandomTableJpaRepository tableRepo;

    @Test
    void buildBundle_assemblesFullCampaign_withBattlemapAssetsAndResolvedNpcFields() {
        // GameSystem avec template PNJ : un TEXT "Histoire" + un KEY_VALUE_LIST "Caracs".
        GameSystemJpaEntity gs = gameSystemRepo.save(GameSystemJpaEntity.builder()
                .name("Test System").isPublic(false)
                .npcTemplate(List.of(
                        TemplateField.text("Histoire"),
                        TemplateField.keyValueList("Caracs", List.of("FOR", "DEX"))))
                .build());

        CampaignJpaEntity camp = campaignRepo.save(CampaignJpaEntity.builder()
                .name("Le baron de la drogue").description("desc").arcsCount(1)
                .gameSystemId(String.valueOf(gs.getId())).build());

        // Assets references par la scene : une image (illustration) + un media + un sidecar.
        ImageJpaEntity img = imageRepo.save(ImageJpaEntity.builder()
                .filename("baron.webp").contentType("image/webp").sizeBytes(100L)
                .storageKey("images/aaa.webp").build());
        StoredFileJpaEntity media = fileRepo.save(StoredFileJpaEntity.builder()
                .filename("convoi.mp4").contentType("video/mp4").sizeBytes(2048L)
                .storageKey("files/bbb.mp4").build());
        StoredFileJpaEntity sidecar = fileRepo.save(StoredFileJpaEntity.builder()
                .filename("convoi.dd2vtt").contentType("application/json").sizeBytes(512L)
                .storageKey("files/ccc.json").build());

        ArcJpaEntity arc = arcRepo.save(ArcJpaEntity.builder()
                .campaignId(camp.getId()).name("Acte I").order(0).build());
        ChapterJpaEntity quest = chapterRepo.save(ChapterJpaEntity.builder()
                .arcId(arc.getId()).name("Le convoi").order(0).build());
        sceneRepo.save(SceneJpaEntity.builder()
                .chapterId(quest.getId()).name("L'attaque du convoi").order(0)
                .playerNarration("Le convoi s'arrete...")
                .illustrationImageIds(List.of(String.valueOf(img.getId())))
                .battlemaps(List.of(new SceneBattlemap("Nuit",
                        String.valueOf(media.getId()), String.valueOf(sidecar.getId()))))
                .build());

        npcRepo.save(NpcJpaEntity.builder()
                .campaignId(camp.getId()).name("Le baron").order(0)
                .values(Map.of("Histoire", "Ancien capitaine de la garde"))
                .keyValueValues(Map.of("Caracs", Map.of("FOR", "16")))
                .build());

        // Table aléatoire (1d6) avec 2 entrées -> RollTable Foundry.
        RandomTableJpaEntity table = RandomTableJpaEntity.builder()
                .campaignId(camp.getId()).name("Rencontres").description("Sur la route").diceFormula("1d6").order(0)
                .build();
        RandomTableEntryJpaEntity e1 = new RandomTableEntryJpaEntity();
        e1.setMinRoll(1); e1.setMaxRoll(3); e1.setLabel("Gobelins"); e1.setDetail("3d4"); e1.setPosition(0);
        e1.setRandomTable(table);
        RandomTableEntryJpaEntity e2 = new RandomTableEntryJpaEntity();
        e2.setMinRoll(4); e2.setMaxRoll(6); e2.setLabel("Rien"); e2.setPosition(1);
        e2.setRandomTable(table);
        table.setEntries(List.of(e1, e2));
        tableRepo.save(table);

        FoundryExportService.BuiltBundle bundle = service.buildBundle(String.valueOf(camp.getId()), "2026-06-25T00:00:00Z");
        FoundryBundle.Data data = bundle.data();

        // Hierarchie
        assertEquals("Le baron de la drogue", data.campaign().name());
        assertEquals(1, data.arcs().size());
        assertEquals(1, data.quests().size());
        assertEquals(1, data.scenes().size());
        assertEquals(String.valueOf(arc.getId()), data.quests().get(0).arcId());
        assertEquals(String.valueOf(quest.getId()), data.scenes().get(0).questId());

        // Battlemaps : refs vers les assets fichiers (prefixe "file-"), label preserve,
        // et champ legacy `battlemap` (1re carte) maintenu pour les modules existants.
        FoundryBundle.Scene scene = data.scenes().get(0);
        assertEquals(1, scene.battlemaps().size());
        assertEquals("Nuit", scene.battlemaps().get(0).label());
        assertEquals("file-" + media.getId(), scene.battlemaps().get(0).mediaAssetId());
        assertEquals("file-" + sidecar.getId(), scene.battlemaps().get(0).dataAssetId());
        assertNotNull(scene.battlemap());
        assertEquals("file-" + media.getId(), scene.battlemap().mediaAssetId());
        assertEquals("file-" + sidecar.getId(), scene.battlemap().dataAssetId());
        assertEquals(List.of("img-" + img.getId()), scene.illustrationAssetIds());

        // Index des assets : 1 image + 2 fichiers, chacun avec un chemin coherent.
        assertEquals(3, data.assets().size());
        assertTrue(data.assets().stream().anyMatch(a ->
                a.id().equals("file-" + media.getId()) && a.kind().equals("battlemapMedia")
                        && a.path().equals("assets/battlemaps/file-" + media.getId() + ".mp4")));
        assertTrue(data.assets().stream().anyMatch(a ->
                a.id().equals("img-" + img.getId()) && a.kind().equals("image")
                        && a.path().equals("assets/images/img-" + img.getId() + ".webp")));

        // Binaires a copier : 3 (1 image + 2 fichiers).
        assertEquals(3, bundle.binaries().size());

        // PNJ : champs resolus via le template (TEXT + KEY_VALUE_LIST).
        assertEquals(1, data.npcs().size());
        List<FoundryBundle.Field> fields = data.npcs().get(0).fields();
        assertTrue(fields.stream().anyMatch(f ->
                "text".equals(f.type()) && "Histoire".equals(f.label())
                        && "Ancien capitaine de la garde".equals(f.value())));
        FoundryBundle.Field caracs = fields.stream()
                .filter(f -> "keyValueList".equals(f.type()) && "Caracs".equals(f.label()))
                .findFirst().orElseThrow();
        assertEquals(1, caracs.entries().size());
        assertEquals("FOR", caracs.entries().get(0).label());
        assertEquals("16", caracs.entries().get(0).value());

        // Table aléatoire -> RollTable (formule + entrées avec intervalles).
        assertEquals(1, data.randomTables().size());
        FoundryBundle.RandomTable rt = data.randomTables().get(0);
        assertEquals("Rencontres", rt.name());
        assertEquals("1d6", rt.diceFormula());
        assertEquals(2, rt.entries().size());
        assertEquals("Gobelins", rt.entries().get(0).label());
        assertEquals(1, rt.entries().get(0).minRoll());
        assertEquals(3, rt.entries().get(0).maxRoll());

        // Manifest
        assertEquals("1.0", bundle.manifest().formatVersion());
        assertEquals("plain", bundle.manifest().contentFormat());
        assertEquals(1, bundle.manifest().counts().get("scenes"));
        assertEquals(1, bundle.manifest().counts().get("randomTables"));
        assertEquals(3, bundle.manifest().counts().get("assets"));
    }

    @Test
    void buildBundle_mapsOnly_stripsJournalsNpcsTablesAndIllustrations() {
        CampaignJpaEntity camp = campaignRepo.save(CampaignJpaEntity.builder()
                .name("Cartes seules").description("d").arcsCount(1).build());
        ImageJpaEntity illus = imageRepo.save(ImageJpaEntity.builder()
                .filename("ambiance.webp").contentType("image/webp").sizeBytes(100L)
                .storageKey("images/ill.webp").build());
        StoredFileJpaEntity media = fileRepo.save(StoredFileJpaEntity.builder()
                .filename("crypte.png").contentType("image/png").sizeBytes(2048L)
                .storageKey("files/crypte.png").build());

        ArcJpaEntity arc = arcRepo.save(ArcJpaEntity.builder()
                .campaignId(camp.getId()).name("Acte I").order(0)
                .illustrationImageIds(List.of(String.valueOf(illus.getId()))).build());
        ChapterJpaEntity chap = chapterRepo.save(ChapterJpaEntity.builder()
                .arcId(arc.getId()).name("La crypte").order(0).build());
        sceneRepo.save(SceneJpaEntity.builder()
                .chapterId(chap.getId()).name("Salle des tombeaux").order(0)
                .illustrationImageIds(List.of(String.valueOf(illus.getId())))
                .battlemaps(List.of(new SceneBattlemap("Nuit", String.valueOf(media.getId()), null)))
                .build());
        npcRepo.save(NpcJpaEntity.builder()
                .campaignId(camp.getId()).name("Le prêtre").order(0).build());
        RandomTableJpaEntity table = RandomTableJpaEntity.builder()
                .campaignId(camp.getId()).name("Rencontres").diceFormula("1d6").order(0).build();
        tableRepo.save(table);

        FoundryExportService.BuiltBundle bundle = service.buildBundle(
                String.valueOf(camp.getId()), "2026-07-03T00:00:00Z",
                new FoundryExportService.ExportOptions(true, false, false));
        FoundryBundle.Data data = bundle.data();

        // Périmètre : cartes+ennemis SEULEMENT — pas de PNJ, pas de tables, options posées.
        assertTrue(data.npcs().isEmpty());
        assertTrue(data.randomTables().isEmpty());
        assertTrue(data.options().maps());
        assertFalse(data.options().journals());
        assertFalse(data.options().tables());

        // L'ossature (arc/quête/scène) reste pour les dossiers, SANS illustrations.
        assertEquals(1, data.arcs().size());
        assertTrue(data.arcs().get(0).illustrationAssetIds().isEmpty());
        FoundryBundle.Scene scene = data.scenes().get(0);
        assertTrue(scene.illustrationAssetIds().isEmpty());
        assertEquals(1, scene.battlemaps().size());
        assertEquals("Nuit", scene.battlemaps().get(0).label());

        // Un SEUL binaire embarqué : la battlemap (l'illustration n'est pas exportée).
        assertEquals(1, bundle.binaries().size());
        assertTrue(data.assets().stream().allMatch(a -> a.kind().startsWith("battlemap")));
    }

    @Test
    void buildBundle_unknownCampaign_throwsNoSuchElement() {
        assertThrows(java.util.NoSuchElementException.class,
                () -> service.buildBundle("999999999", "2026-06-25T00:00:00Z"));
    }
}
