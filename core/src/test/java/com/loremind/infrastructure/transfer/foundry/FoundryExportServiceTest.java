package com.loremind.infrastructure.transfer.foundry;

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
                .battlemapMediaFileId(String.valueOf(media.getId()))
                .battlemapDataFileId(String.valueOf(sidecar.getId()))
                .build());

        npcRepo.save(NpcJpaEntity.builder()
                .campaignId(camp.getId()).name("Le baron").order(0)
                .values(Map.of("Histoire", "Ancien capitaine de la garde"))
                .keyValueValues(Map.of("Caracs", Map.of("FOR", "16")))
                .build());

        FoundryExportService.BuiltBundle bundle = service.buildBundle(String.valueOf(camp.getId()), "2026-06-25T00:00:00Z");
        FoundryBundle.Data data = bundle.data();

        // Hierarchie
        assertEquals("Le baron de la drogue", data.campaign().name());
        assertEquals(1, data.arcs().size());
        assertEquals(1, data.quests().size());
        assertEquals(1, data.scenes().size());
        assertEquals(String.valueOf(arc.getId()), data.quests().get(0).arcId());
        assertEquals(String.valueOf(quest.getId()), data.scenes().get(0).questId());

        // Battlemap : refs vers les assets fichiers (prefixe "file-").
        FoundryBundle.Scene scene = data.scenes().get(0);
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

        // Manifest
        assertEquals("1.0", bundle.manifest().formatVersion());
        assertEquals("plain", bundle.manifest().contentFormat());
        assertEquals(1, bundle.manifest().counts().get("scenes"));
        assertEquals(3, bundle.manifest().counts().get("assets"));
    }

    @Test
    void buildBundle_unknownCampaign_throwsNoSuchElement() {
        assertThrows(java.util.NoSuchElementException.class,
                () -> service.buildBundle("999999999", "2026-06-25T00:00:00Z"));
    }
}
