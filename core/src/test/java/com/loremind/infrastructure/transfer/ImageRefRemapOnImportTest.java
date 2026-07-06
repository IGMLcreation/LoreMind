package com.loremind.infrastructure.transfer;

import com.loremind.domain.campaigncontext.structure.Room;
import com.loremind.domain.campaigncontext.structure.SceneBattlemap;
import com.loremind.domain.files.ports.FileStorage;
import com.loremind.domain.images.ports.ImageStorage;
import com.loremind.domain.lorecontext.ImageFraming;
import com.loremind.infrastructure.persistence.entity.ArcJpaEntity;
import com.loremind.infrastructure.persistence.entity.CampaignJpaEntity;
import com.loremind.infrastructure.persistence.entity.ChapterJpaEntity;
import com.loremind.infrastructure.persistence.entity.ImageJpaEntity;
import com.loremind.infrastructure.persistence.entity.LoreJpaEntity;
import com.loremind.infrastructure.persistence.entity.LoreNodeJpaEntity;
import com.loremind.infrastructure.persistence.entity.NpcJpaEntity;
import com.loremind.infrastructure.persistence.entity.PageJpaEntity;
import com.loremind.infrastructure.persistence.entity.SceneJpaEntity;
import com.loremind.infrastructure.persistence.entity.StoredFileJpaEntity;
import com.loremind.infrastructure.persistence.jpa.ArcJpaRepository;
import com.loremind.infrastructure.persistence.jpa.CampaignJpaRepository;
import com.loremind.infrastructure.persistence.jpa.ChapterJpaRepository;
import com.loremind.infrastructure.persistence.jpa.ImageJpaRepository;
import com.loremind.infrastructure.persistence.jpa.LoreJpaRepository;
import com.loremind.infrastructure.persistence.jpa.LoreNodeJpaRepository;
import com.loremind.infrastructure.persistence.jpa.NpcJpaRepository;
import com.loremind.infrastructure.persistence.jpa.PageJpaRepository;
import com.loremind.infrastructure.persistence.jpa.SceneJpaRepository;
import com.loremind.infrastructure.persistence.jpa.StoredFileJpaRepository;
import com.loremind.infrastructure.transfer.dto.ContentExport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.mockito.Mockito.when;

/**
 * Régression : import d'un export sur une base où les images/fichiers reçoivent un
 * NOUVEL id (cas réel « fixe → portable »). Le round-trip classique réimporte dans la
 * MÊME base : la clé de stockage existe déjà, l'id d'image ne bouge pas, et le bug de
 * refs non remappées reste invisible.
 * <p>
 * Ici on exporte, puis on SUPPRIME les lignes {@code images}/{@code stored_files} (le
 * binaire reste fourni par le mock de stockage). Réimportées, elles obtiennent un id
 * différent (les colonnes IDENTITY H2 ne réutilisent pas un id libéré) — exactement ce
 * qui se passe sur une autre machine. On vérifie alors que TOUTES les réfs d'images et
 * de fichiers des entités importées pointent le nouvel id, pas l'ancien (images absentes
 * / mélangées avant le correctif).
 */
@SpringBootTest
@Transactional
class ImageRefRemapOnImportTest {

    @Autowired private ExportService exportService;
    @Autowired private ImportService importService;

    @Autowired private CampaignJpaRepository campaignRepo;
    @Autowired private ArcJpaRepository arcRepo;
    @Autowired private ChapterJpaRepository chapterRepo;
    @Autowired private SceneJpaRepository sceneRepo;
    @Autowired private NpcJpaRepository npcRepo;
    @Autowired private LoreJpaRepository loreRepo;
    @Autowired private LoreNodeJpaRepository loreNodeRepo;
    @Autowired private PageJpaRepository pageRepo;
    @Autowired private ImageJpaRepository imageRepo;
    @Autowired private StoredFileJpaRepository storedFileRepo;

    @MockitoBean private ImageStorage imageStorage;
    @MockitoBean private FileStorage fileStorage;

    private static final String IMG_KEY = "images/remap-portrait.png";
    private static final String FILE_KEY = "files/remap-map.json";

    @Test
    void importIntoDbWithFreshIds_remapsEveryImageAndFileRef() {
        // ----- Seed : image + fichier référencés par un peu tout -----
        ImageJpaEntity image = imageRepo.save(ImageJpaEntity.builder()
                .filename("portrait.png").contentType("image/png").sizeBytes(4).storageKey(IMG_KEY).build());
        String oldImageRef = String.valueOf(image.getId());
        StoredFileJpaEntity file = storedFileRepo.save(StoredFileJpaEntity.builder()
                .filename("map.json").contentType("application/json").sizeBytes(2).storageKey(FILE_KEY).build());
        String oldFileRef = String.valueOf(file.getId());

        CampaignJpaEntity campaign = campaignRepo.save(CampaignJpaEntity.builder()
                .name("Remap Campaign").description("d").arcsCount(1).build());

        Long originalNpcId = npcRepo.save(NpcJpaEntity.builder()
                .name("Remap Npc").campaignId(campaign.getId())
                .portraitImageId(oldImageRef)
                .headerImageId(oldImageRef)
                .imageValues(Map.of("gallery", List.of(oldImageRef)))
                .folder("Ville").order(0).build()).getId();

        ArcJpaEntity arc = arcRepo.save(ArcJpaEntity.builder()
                .name("Remap Arc").campaignId(campaign.getId()).order(0)
                .illustrationImageIds(new ArrayList<>(List.of(oldImageRef))).build());
        ChapterJpaEntity chapter = chapterRepo.save(ChapterJpaEntity.builder()
                .name("Remap Chapter").arcId(arc.getId()).order(0).build());

        Room room = Room.builder().id("room-uuid-1").name("Crypte").order(0)
                .mapImageId(oldImageRef)
                .illustrationImageIds(new ArrayList<>(List.of(oldImageRef))).build();
        Long originalSceneId = sceneRepo.save(SceneJpaEntity.builder()
                .name("Remap Scene").chapterId(chapter.getId()).order(0)
                .illustrationImageIds(new ArrayList<>(List.of(oldImageRef)))
                .battlemaps(new ArrayList<>(List.of(new SceneBattlemap("Jour", oldFileRef, null))))
                .rooms(new ArrayList<>(List.of(room))).build()).getId();

        // Page de lore : galerie + cadrage (fieldKey -> imageId -> pan/zoom) indexé par id d'image.
        LoreJpaEntity lore = loreRepo.save(LoreJpaEntity.builder().name("Remap Lore").description("d").build());
        LoreNodeJpaEntity node = loreNodeRepo.save(LoreNodeJpaEntity.builder()
                .name("Remap Node").loreId(lore.getId()).order(0).build());
        Long originalPageId = pageRepo.save(PageJpaEntity.builder()
                .loreId(lore.getId()).nodeId(node.getId()).title("Remap Page")
                .imageValues(Map.of("gallery", List.of(oldImageRef)))
                .imageFraming(Map.of("gallery", Map.of(oldImageRef, new ImageFraming(50.0, 30.0, 1.4))))
                .build()).getId();

        when(imageStorage.download(IMG_KEY)).thenAnswer(inv -> new ByteArrayInputStream("PNG!".getBytes()));
        when(fileStorage.download(FILE_KEY)).thenAnswer(inv -> new ByteArrayInputStream("{}".getBytes()));

        // ----- Export + zip -----
        ContentExport export = exportService.buildExport("2026-01-02T00:00:00Z");
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.writeZip(export, baos);

        // ----- Simule une base cible : les clés n'existent plus -> id neuf à la réinsertion -----
        imageRepo.deleteById(image.getId());
        storedFileRepo.deleteById(file.getId());
        imageRepo.flush();
        storedFileRepo.flush();

        // ----- Import -----
        importService.importZip(new ByteArrayInputStream(baos.toByteArray()));

        // Image/fichier recréés sous leur clé, mais avec un NOUVEL id.
        ImageJpaEntity newImage = imageRepo.findByStorageKey(IMG_KEY).orElseThrow();
        String newImageRef = String.valueOf(newImage.getId());
        assertNotEquals(oldImageRef, newImageRef, "l'image importée doit avoir un id neuf");
        StoredFileJpaEntity newFile = storedFileRepo.findByStorageKey(FILE_KEY).orElseThrow();
        String newFileRef = String.valueOf(newFile.getId());
        assertNotEquals(oldFileRef, newFileRef, "le fichier importé doit avoir un id neuf");

        // NPC importé : portrait + header + galerie pointent la nouvelle image.
        NpcJpaEntity importedNpc = npcRepo.findAll().stream()
                .filter(n -> "Remap Npc".equals(n.getName()) && !n.getId().equals(originalNpcId))
                .findFirst().orElseThrow();
        assertEquals(newImageRef, importedNpc.getPortraitImageId(), "portrait NPC remappé");
        assertEquals(newImageRef, importedNpc.getHeaderImageId(), "header NPC remappé");
        assertEquals(List.of(newImageRef), importedNpc.getImageValues().get("gallery"), "galerie NPC remappée");

        // Arc importé : illustration remappée.
        ArcJpaEntity importedArc = arcRepo.findAll().stream()
                .filter(a -> "Remap Arc".equals(a.getName()) && !a.getId().equals(arc.getId()))
                .findFirst().orElseThrow();
        assertEquals(List.of(newImageRef), importedArc.getIllustrationImageIds(), "illustration Arc remappée");

        // Scène importée : illustration + battlemap (fichier) + images de salle remappées.
        SceneJpaEntity importedScene = sceneRepo.findAll().stream()
                .filter(s -> "Remap Scene".equals(s.getName()) && !s.getId().equals(originalSceneId))
                .findFirst().orElseThrow();
        assertEquals(List.of(newImageRef), importedScene.getIllustrationImageIds(), "illustration Scène remappée");
        assertEquals(newFileRef, importedScene.getBattlemaps().get(0).mediaFileId(), "battlemap Scène remappée");
        Room importedRoom = importedScene.getRooms().get(0);
        assertEquals(newImageRef, importedRoom.getMapImageId(), "plan de salle remappé");
        assertEquals(List.of(newImageRef), importedRoom.getIllustrationImageIds(), "galerie de salle remappée");

        // Page importée : galerie ET cadrage (clé interne = id d'image) remappés vers la nouvelle image.
        PageJpaEntity importedPage = pageRepo.findAll().stream()
                .filter(p -> "Remap Page".equals(p.getTitle()) && !p.getId().equals(originalPageId))
                .findFirst().orElseThrow();
        assertEquals(List.of(newImageRef), importedPage.getImageValues().get("gallery"), "galerie Page remappée");
        assertEquals(Map.of(newImageRef, new ImageFraming(50.0, 30.0, 1.4)),
                importedPage.getImageFraming().get("gallery"),
                "le cadrage doit être ré-indexé sous le nouvel id d'image");
    }
}
