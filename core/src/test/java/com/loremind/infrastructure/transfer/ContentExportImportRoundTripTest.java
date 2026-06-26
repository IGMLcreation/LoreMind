package com.loremind.infrastructure.transfer;

import com.loremind.domain.campaigncontext.ArcType;
import com.loremind.domain.campaigncontext.Prerequisite;
import com.loremind.domain.campaigncontext.SceneBranch;
import com.loremind.domain.files.ports.FileStorage;
import com.loremind.domain.images.ports.ImageStorage;
import com.loremind.infrastructure.persistence.entity.*;
import com.loremind.infrastructure.persistence.jpa.*;
import com.loremind.infrastructure.transfer.dto.ContentExport;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Aller-retour EXPORT → ZIP → IMPORT (mode FUSION) du contenu portable.
 * <p>
 * On sème un graphe complet (système de jeu, lore/dossiers/template/page, campagne,
 * arc, chapitres avec prérequis, scène avec branches/ennemis/battlemap, PNJ, ennemi,
 * personnage, catalogue d'objets, table aléatoire, image + fichier), on l'exporte,
 * on le réimporte, puis on vérifie :
 * <ul>
 *   <li>le zip embarque {@code manifest.json}, {@code data.json} et les binaires
 *       d'images/fichiers RÉFÉRENCÉS ;</li>
 *   <li>l'import DOUBLE chaque type (l'export étant global) ;</li>
 *   <li>les clés d'images (uniques) sont RÉUTILISÉES, pas dupliquées ;</li>
 *   <li>les références sont remappées en 2e passe (l'arc importé pointe la page
 *       importée, pas l'originale) ;</li>
 *   <li>le {@code playthroughId} d'un personnage est remis à null à l'import.</li>
 * </ul>
 * Le stockage binaire (MinIO) est mocké : aucun appel réseau, et on contrôle ce que
 * {@code download} renvoie pour les clés référencées.
 */
@SpringBootTest
@Transactional
class ContentExportImportRoundTripTest {

    @Autowired private ExportService exportService;
    @Autowired private ImportService importService;

    @Autowired private GameSystemJpaRepository gameSystemRepo;
    @Autowired private LoreJpaRepository loreRepo;
    @Autowired private LoreNodeJpaRepository loreNodeRepo;
    @Autowired private TemplateJpaRepository templateRepo;
    @Autowired private PageJpaRepository pageRepo;
    @Autowired private CampaignJpaRepository campaignRepo;
    @Autowired private ArcJpaRepository arcRepo;
    @Autowired private ChapterJpaRepository chapterRepo;
    @Autowired private SceneJpaRepository sceneRepo;
    @Autowired private CharacterJpaRepository characterRepo;
    @Autowired private NpcJpaRepository npcRepo;
    @Autowired private EnemyJpaRepository enemyRepo;
    @Autowired private ItemCatalogJpaRepository itemCatalogRepo;
    @Autowired private RandomTableJpaRepository randomTableRepo;
    @Autowired private ImageJpaRepository imageRepo;
    @Autowired private StoredFileJpaRepository storedFileRepo;

    @MockitoBean private ImageStorage imageStorage;
    @MockitoBean private FileStorage fileStorage;

    private static final String IMG_KEY = "images/round-trip-abc.png";
    private static final String FILE_KEY = "files/round-trip-map.json";

    @Test
    void roundTrip_duplicatesEveryContentTypeAndRemapsReferences() throws IOException {
        // ----- 1. Graphe de contenu riche -----
        GameSystemJpaEntity gs = gameSystemRepo.save(GameSystemJpaEntity.builder()
                .name("RT System").description("d").foundryActorType("npc").isPublic(true).build());

        LoreJpaEntity lore = loreRepo.save(LoreJpaEntity.builder().name("RT Lore").description("d").build());
        LoreNodeJpaEntity rootNode = loreNodeRepo.save(LoreNodeJpaEntity.builder()
                .name("RT Root").loreId(lore.getId()).order(0).build());
        loreNodeRepo.save(LoreNodeJpaEntity.builder()
                .name("RT Child").loreId(lore.getId()).parentId(rootNode.getId()).order(1).build());
        TemplateJpaEntity template = templateRepo.save(TemplateJpaEntity.builder()
                .loreId(lore.getId()).name("RT Template").defaultNodeId(rootNode.getId()).build());
        PageJpaEntity page = pageRepo.save(PageJpaEntity.builder()
                .loreId(lore.getId()).nodeId(rootNode.getId()).templateId(template.getId())
                .title("RT Page").imageValues(Map.of("gallery", List.of(IMG_KEY))).build());
        // Auto-référence : vérifie le remap des relatedPageIds (2e passe).
        page.setRelatedPageIds(new ArrayList<>(List.of(String.valueOf(page.getId()))));
        pageRepo.save(page);

        imageRepo.save(ImageJpaEntity.builder()
                .filename("abc.png").contentType("image/png").sizeBytes(7).storageKey(IMG_KEY).build());
        StoredFileJpaEntity storedFile = storedFileRepo.save(StoredFileJpaEntity.builder()
                .filename("map.json").contentType("application/json").sizeBytes(6).storageKey(FILE_KEY).build());

        CampaignJpaEntity campaign = campaignRepo.save(CampaignJpaEntity.builder()
                .name("RT Campaign").description("d").arcsCount(1)
                .loreId(String.valueOf(lore.getId())).gameSystemId(String.valueOf(gs.getId())).build());

        ArcJpaEntity arc = arcRepo.save(ArcJpaEntity.builder()
                .name("RT Arc").campaignId(campaign.getId()).order(0).type(ArcType.HUB)
                .relatedPageIds(new ArrayList<>(List.of(String.valueOf(page.getId()))))
                .illustrationImageIds(new ArrayList<>(List.of(IMG_KEY))).build());

        ChapterJpaEntity chapterA = chapterRepo.save(ChapterJpaEntity.builder()
                .name("RT Chapter A").arcId(arc.getId()).order(0).build());
        chapterRepo.save(ChapterJpaEntity.builder()
                .name("RT Chapter B").arcId(arc.getId()).order(1)
                .prerequisites(new ArrayList<>(List.of(new Prerequisite.QuestCompleted(String.valueOf(chapterA.getId())))))
                .relatedPageIds(new ArrayList<>(List.of(String.valueOf(page.getId())))).build());
        ChapterJpaEntity chapterB = chapterRepo.findAll().stream()
                .filter(c -> "RT Chapter B".equals(c.getName())).findFirst().orElseThrow();

        EnemyJpaEntity enemy = enemyRepo.save(EnemyJpaEntity.builder()
                .name("RT Enemy").campaignId(campaign.getId()).level("3").folder("Cave")
                .foundryRef("Compendium.x").foundryStats(Map.of("hp", "11")).order(0).build());

        sceneRepo.save(SceneJpaEntity.builder()
                .name("RT Scene").chapterId(chapterB.getId()).order(0)
                .enemyIds(new ArrayList<>(List.of(String.valueOf(enemy.getId()))))
                .relatedPageIds(new ArrayList<>(List.of(String.valueOf(page.getId()))))
                .illustrationImageIds(new ArrayList<>(List.of(IMG_KEY)))
                .battlemapMediaFileId(String.valueOf(storedFile.getId()))
                .branches(new ArrayList<>(List.of(new SceneBranch("Si fuite", null, "cond")))).build());

        npcRepo.save(NpcJpaEntity.builder()
                .name("RT Npc").campaignId(campaign.getId()).portraitImageId(IMG_KEY).folder("Ville").order(0)
                .relatedPageIds(new ArrayList<>(List.of(String.valueOf(page.getId())))).build());

        characterRepo.save(CharacterJpaEntity.builder()
                .name("RT Hero").campaignId(campaign.getId()).playthroughId(999L).order(0).build());

        ItemCatalogJpaEntity catalog = ItemCatalogJpaEntity.builder()
                .name("RT Catalog").campaignId(campaign.getId()).order(0).build();
        CatalogItemJpaEntity item = CatalogItemJpaEntity.builder()
                .name("Sword").price("10 gp").category("weapon").position(0).catalog(catalog).build();
        catalog.setItems(new ArrayList<>(List.of(item)));
        itemCatalogRepo.save(catalog);

        RandomTableJpaEntity table = RandomTableJpaEntity.builder()
                .name("RT Table").campaignId(campaign.getId()).diceFormula("1d6").order(0).build();
        RandomTableEntryJpaEntity entry = RandomTableEntryJpaEntity.builder()
                .minRoll(1).maxRoll(3).label("Gobelins").position(0).randomTable(table).build();
        table.setEntries(new ArrayList<>(List.of(entry)));
        randomTableRepo.save(table);

        // Comptes AVANT export (incluent un éventuel seed) — l'export est GLOBAL.
        long campaignsBefore = campaignRepo.count();
        long arcsBefore = arcRepo.count();
        long chaptersBefore = chapterRepo.count();
        long scenesBefore = sceneRepo.count();
        long npcsBefore = npcRepo.count();
        long enemiesBefore = enemyRepo.count();
        long pagesBefore = pageRepo.count();
        long gsBefore = gameSystemRepo.count();
        long imagesBefore = imageRepo.count();
        Long campaignId0 = campaign.getId();
        Long pageId0 = page.getId();

        when(imageStorage.download(IMG_KEY)).thenAnswer(inv -> new ByteArrayInputStream("PNGDATA".getBytes()));
        when(fileStorage.download(FILE_KEY)).thenAnswer(inv -> new ByteArrayInputStream("{\"x\":1}".getBytes()));

        // ----- 2. Export logique + sérialisation ZIP -----
        ContentExport export = exportService.buildExport("2026-01-02T03:04:05Z");
        assertEquals(1, export.manifest().formatVersion());
        assertEquals("2026-01-02T03:04:05Z", export.manifest().exportedAt());
        assertNotNull(export.manifest().appVersion());
        assertFalse(export.campaigns().isEmpty());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.writeZip(export, baos);
        Map<String, byte[]> zip = readZip(baos.toByteArray());
        assertTrue(zip.containsKey("manifest.json"));
        assertTrue(zip.containsKey("data.json"));
        // Binaire image référencé (portrait/illustration) embarqué + fichier de battlemap.
        assertTrue(zip.containsKey("images/" + IMG_KEY), "binaire image référencé attendu dans le zip");
        assertTrue(zip.containsKey("files/" + FILE_KEY), "binaire fichier référencé attendu dans le zip");

        // ----- 3. Import (mode fusion : nouveaux ids) -----
        ImportResult result = importService.importZip(new ByteArrayInputStream(baos.toByteArray()));

        // Tout le contenu exporté est ré-inséré → chaque type est doublé.
        assertEquals(2 * campaignsBefore, campaignRepo.count());
        assertEquals(2 * arcsBefore, arcRepo.count());
        assertEquals(2 * chaptersBefore, chapterRepo.count());
        assertEquals(2 * scenesBefore, sceneRepo.count());
        assertEquals(2 * npcsBefore, npcRepo.count());
        assertEquals(2 * enemiesBefore, enemyRepo.count());
        assertEquals(2 * pagesBefore, pageRepo.count());
        assertEquals(2 * gsBefore, gameSystemRepo.count());
        // Clé image UNIQUE déjà présente → réutilisée, pas de doublon.
        assertEquals(imagesBefore, imageRepo.count());
        assertTrue(result.imagesReused() >= 1, "l'image référencée doit être réutilisée");
        assertEquals((int) campaignsBefore, result.created().get("campaigns"));

        // ----- 4. Remapping des références (2e passe) -----
        // 4a. L'arc importé référence la PAGE importée (remappée), pas l'originale.
        Long importedCampaignId = onlyOther(campaignRepo.findAll().stream()
                .filter(c -> "RT Campaign".equals(c.getName())).map(CampaignJpaEntity::getId).toList(), campaignId0);
        Long importedPageId = onlyOther(pageRepo.findAll().stream()
                .filter(p -> "RT Page".equals(p.getTitle())).map(PageJpaEntity::getId).toList(), pageId0);
        ArcJpaEntity importedArc = arcRepo.findAll().stream()
                .filter(a -> "RT Arc".equals(a.getName()) && importedCampaignId.equals(a.getCampaignId()))
                .findFirst().orElseThrow();
        assertTrue(importedArc.getRelatedPageIds().contains(String.valueOf(importedPageId)),
                "l'arc importé doit pointer la page importée (remap), pas l'originale");
        assertFalse(importedArc.getRelatedPageIds().contains(String.valueOf(pageId0)));

        // 4b. Le personnage importé a son playthroughId remis à null (hors périmètre).
        List<CharacterJpaEntity> heroes = characterRepo.findAll().stream()
                .filter(c -> "RT Hero".equals(c.getName())).toList();
        assertEquals(2, heroes.size());
        assertTrue(heroes.stream().anyMatch(c -> c.getPlaythroughId() == null));
        assertTrue(heroes.stream().anyMatch(c -> Long.valueOf(999L).equals(c.getPlaythroughId())));
    }

    /** Lit toutes les entrées (nom → octets) d'un zip en mémoire. */
    private static Map<String, byte[]> readZip(byte[] bytes) throws IOException {
        Map<String, byte[]> out = new LinkedHashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(bytes))) {
            ZipEntry e;
            while ((e = zip.getNextEntry()) != null) {
                if (!e.isDirectory()) {
                    ByteArrayOutputStream buf = new ByteArrayOutputStream();
                    zip.transferTo(buf);
                    out.put(e.getName(), buf.toByteArray());
                }
                zip.closeEntry();
            }
        }
        return out;
    }

    /** Renvoie l'unique id de la liste différent de {@code original} (l'élément importé). */
    private static Long onlyOther(List<Long> ids, Long original) {
        return ids.stream().filter(id -> !id.equals(original)).findFirst().orElseThrow();
    }
}
