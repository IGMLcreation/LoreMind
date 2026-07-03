package com.loremind.infrastructure.transfer;

import com.loremind.domain.campaigncontext.ArcType;
import com.loremind.domain.campaigncontext.NodeType;
import com.loremind.domain.campaigncontext.Prerequisite;
import com.loremind.domain.campaigncontext.QuestNodeRef;
import com.loremind.domain.campaigncontext.ProgressionStatus;
import com.loremind.domain.campaigncontext.SceneBattlemap;
import com.loremind.domain.campaigncontext.SceneBranch;
import com.loremind.domain.files.ports.FileStorage;
import com.loremind.domain.images.ports.ImageStorage;
import com.loremind.domain.playcontext.ClockTrigger;
import com.loremind.domain.playcontext.EntryType;
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
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Aller-retour EXPORT → ZIP → IMPORT (mode FUSION) du contenu portable, JEU COMPRIS.
 * <p>
 * On sème un graphe complet — prep (système, lore/dossiers/template/page, campagne, arc,
 * chapitres avec prérequis, scène, PNJ, ennemi) ET espace de jeu (partie, séance, journal,
 * flag, progression de quête, feuille de perso) — plus une image et un fichier. On l'exporte,
 * on le réimporte, puis on vérifie :
 * <ul>
 *   <li>le zip embarque manifest/data + les binaires d'images (référencées par <b>ID</b>,
 *       résolu en clé de stockage) et de fichiers RÉFÉRENCÉS ;</li>
 *   <li>l'import DOUBLE chaque type, jeu compris ;</li>
 *   <li>les références sont remappées : l'arc importé pointe la page importée, et la feuille
 *       de perso / séance / progression importées pointent la Partie / le chapitre importés.</li>
 * </ul>
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
    @Autowired private PlaythroughJpaRepository playthroughRepo;
    @Autowired private SessionJpaRepository sessionRepo;
    @Autowired private SessionEntryJpaRepository sessionEntryRepo;
    @Autowired private ClockJpaRepository clockRepo;
    @Autowired private FrontJpaRepository frontRepo;
    @Autowired private PlaythroughFlagJpaRepository playthroughFlagRepo;
    @Autowired private QuestProgressionJpaRepository questProgressionRepo;
    @Autowired private QuestJpaRepository questRepo;

    @MockitoBean private ImageStorage imageStorage;
    @MockitoBean private FileStorage fileStorage;

    private static final String IMG_KEY = "images/round-trip-abc.png";
    private static final String FILE_KEY = "files/round-trip-map.json";

    @Test
    void roundTrip_duplicatesEveryContentTypeIncludingPlayAndRemapsReferences() throws IOException {
        // ----- 1. Prep -----
        GameSystemJpaEntity gs = gameSystemRepo.save(GameSystemJpaEntity.builder()
                .name("RT System").description("d").foundryActorType("npc").isPublic(true).build());

        LoreJpaEntity lore = loreRepo.save(LoreJpaEntity.builder().name("RT Lore").description("d").build());
        LoreNodeJpaEntity rootNode = loreNodeRepo.save(LoreNodeJpaEntity.builder()
                .name("RT Root").loreId(lore.getId()).order(0).build());
        loreNodeRepo.save(LoreNodeJpaEntity.builder()
                .name("RT Child").loreId(lore.getId()).parentId(rootNode.getId()).order(1).build());
        TemplateJpaEntity template = templateRepo.save(TemplateJpaEntity.builder()
                .loreId(lore.getId()).name("RT Template").defaultNodeId(rootNode.getId()).build());

        // L'image est référencée par son ID (convention de prod), pas par sa clé de stockage.
        ImageJpaEntity image = imageRepo.save(ImageJpaEntity.builder()
                .filename("abc.png").contentType("image/png").sizeBytes(7).storageKey(IMG_KEY).build());
        String imageRef = String.valueOf(image.getId());
        StoredFileJpaEntity storedFile = storedFileRepo.save(StoredFileJpaEntity.builder()
                .filename("map.json").contentType("application/json").sizeBytes(6).storageKey(FILE_KEY).build());

        PageJpaEntity page = pageRepo.save(PageJpaEntity.builder()
                .loreId(lore.getId()).nodeId(rootNode.getId()).templateId(template.getId())
                .title("RT Page").imageValues(Map.of("gallery", List.of(imageRef))).build());
        page.setRelatedPageIds(new ArrayList<>(List.of(String.valueOf(page.getId())))); // auto-réf -> remap
        pageRepo.save(page);

        CampaignJpaEntity campaign = campaignRepo.save(CampaignJpaEntity.builder()
                .name("RT Campaign").description("d").arcsCount(1)
                .loreId(String.valueOf(lore.getId())).gameSystemId(String.valueOf(gs.getId())).build());

        ArcJpaEntity arc = arcRepo.save(ArcJpaEntity.builder()
                .name("RT Arc").campaignId(campaign.getId()).order(0).type(ArcType.HUB)
                .relatedPageIds(new ArrayList<>(List.of(String.valueOf(page.getId()))))
                .illustrationImageIds(new ArrayList<>(List.of(imageRef))).build());

        ChapterJpaEntity chapterA = chapterRepo.save(ChapterJpaEntity.builder()
                .name("RT Chapter A").arcId(arc.getId()).order(0).build());
        chapterRepo.save(ChapterJpaEntity.builder()
                .name("RT Chapter B").arcId(arc.getId()).order(1)
                .relatedPageIds(new ArrayList<>(List.of(String.valueOf(page.getId())))).build());
        ChapterJpaEntity chapterB = chapterRepo.findAll().stream()
                .filter(c -> "RT Chapter B".equals(c.getName())).findFirst().orElseThrow();

        // Quête (Niveau 1) : prérequis (flag), nœud vers chapterA (à remapper), page liée (à remapper).
        QuestJpaEntity quest = questRepo.save(QuestJpaEntity.builder()
                .campaignId(campaign.getId()).name("RT Quest").order(0)
                .prerequisites(new ArrayList<>(List.of(new Prerequisite.FlagSet("porte_ouverte"))))
                .nodes(new ArrayList<>(List.of(new QuestNodeRef(NodeType.CHAPTER, String.valueOf(chapterA.getId()), 0))))
                .relatedPageIds(new ArrayList<>(List.of(String.valueOf(page.getId())))).build());

        EnemyJpaEntity enemy = enemyRepo.save(EnemyJpaEntity.builder()
                .name("RT Enemy").campaignId(campaign.getId()).level("3").folder("Cave")
                .foundryRef("Compendium.x").foundryStats(Map.of("hp", "11")).order(0).build());

        sceneRepo.save(SceneJpaEntity.builder()
                .name("RT Scene").chapterId(chapterB.getId()).order(0)
                .enemyIds(new ArrayList<>(List.of(String.valueOf(enemy.getId()))))
                .relatedPageIds(new ArrayList<>(List.of(String.valueOf(page.getId()))))
                .illustrationImageIds(new ArrayList<>(List.of(imageRef)))
                .battlemaps(new ArrayList<>(List.of(
                        new SceneBattlemap("Nuit", String.valueOf(storedFile.getId()), null))))
                .branches(new ArrayList<>(List.of(new SceneBranch("Si fuite", null, "cond")))).build());

        npcRepo.save(NpcJpaEntity.builder()
                .name("RT Npc").campaignId(campaign.getId()).portraitImageId(imageRef).folder("Ville").order(0)
                .relatedPageIds(new ArrayList<>(List.of(String.valueOf(page.getId())))).build());

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

        // ----- 1b. Espace de jeu -----
        PlaythroughJpaEntity pt = playthroughRepo.save(PlaythroughJpaEntity.builder()
                .campaignId(campaign.getId()).name("RT Partie").description("d").build());
        SessionJpaEntity session = sessionRepo.save(SessionJpaEntity.builder()
                .name("RT Séance").campaignId(String.valueOf(campaign.getId())).playthroughId(pt.getId())
                .startedAt(LocalDateTime.of(2026, 1, 1, 20, 0)).build());
        sessionEntryRepo.save(SessionEntryJpaEntity.builder()
                .sessionId(String.valueOf(session.getId())).type(EntryType.NOTE).content("Début")
                .occurredAt(LocalDateTime.of(2026, 1, 1, 20, 5)).build());
        playthroughFlagRepo.save(PlaythroughFlagJpaEntity.builder()
                .playthroughId(pt.getId()).name("porte_ouverte").value(true).build());
        FrontJpaEntity front = frontRepo.save(FrontJpaEntity.builder()
                .playthroughId(pt.getId()).name("RT Front").description("menace").order(0).build());
        clockRepo.save(ClockJpaEntity.builder()
                .playthroughId(pt.getId()).name("RT Horloge").description("Quand pleine : boom")
                .segments(6).filled(2).order(0)
                .triggerType(ClockTrigger.QUEST_COMPLETED).triggerRef(String.valueOf(quest.getId()))
                .frontId(front.getId()).build());
        // Progression d'une VRAIE quête (modèle Niveau 1) : référence le quest id.
        questProgressionRepo.save(QuestProgressionJpaEntity.builder()
                .playthroughId(pt.getId()).questId(quest.getId()).status(ProgressionStatus.IN_PROGRESS).build());
        characterRepo.save(CharacterJpaEntity.builder()
                .name("RT Hero").campaignId(campaign.getId()).playthroughId(pt.getId()).order(0).build());

        // Comptes AVANT export (l'export complet est GLOBAL → tout sera doublé).
        long campaignsBefore = campaignRepo.count();
        long arcsBefore = arcRepo.count();
        long pagesBefore = pageRepo.count();
        long imagesBefore = imageRepo.count();
        long playthroughsBefore = playthroughRepo.count();
        long sessionsBefore = sessionRepo.count();
        long entriesBefore = sessionEntryRepo.count();
        long flagsBefore = playthroughFlagRepo.count();
        long questsBefore = questProgressionRepo.count();
        long questEntitiesBefore = questRepo.count();
        long charactersBefore = characterRepo.count();
        Long campaignId0 = campaign.getId();
        Long pageId0 = page.getId();
        Long ptId0 = pt.getId();
        Long chapterAId0 = chapterA.getId();

        when(imageStorage.download(IMG_KEY)).thenAnswer(inv -> new ByteArrayInputStream("PNGDATA".getBytes()));
        when(fileStorage.download(FILE_KEY)).thenAnswer(inv -> new ByteArrayInputStream("{\"x\":1}".getBytes()));

        // ----- 2. Export + ZIP -----
        ContentExport export = exportService.buildExport("2026-01-02T03:04:05Z");
        assertEquals(2, export.manifest().formatVersion());
        assertEquals("complète", export.manifest().scope());
        assertFalse(export.playthroughs().isEmpty());

        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        exportService.writeZip(export, baos);
        Map<String, byte[]> zip = readZip(baos.toByteArray());
        assertTrue(zip.containsKey("manifest.json"));
        assertTrue(zip.containsKey("data.json"));
        // Le binaire image référencé par ID est bien résolu vers sa clé puis embarqué.
        assertTrue(zip.containsKey("images/" + IMG_KEY), "binaire image (réf par ID) attendu dans le zip");
        assertTrue(zip.containsKey("files/" + FILE_KEY), "binaire fichier référencé attendu dans le zip");

        // ----- 3. Import (fusion) -----
        ImportResult result = importService.importZip(new ByteArrayInputStream(baos.toByteArray()));

        assertEquals(2 * campaignsBefore, campaignRepo.count());
        assertEquals(2 * arcsBefore, arcRepo.count());
        assertEquals(2 * pagesBefore, pageRepo.count());
        assertEquals(imagesBefore, imageRepo.count()); // clé unique réutilisée
        assertTrue(result.imagesReused() >= 1);
        // Espace de jeu également dupliqué.
        assertEquals(2 * playthroughsBefore, playthroughRepo.count());
        assertEquals(2 * sessionsBefore, sessionRepo.count());
        assertEquals(2 * entriesBefore, sessionEntryRepo.count());
        assertEquals(2 * flagsBefore, playthroughFlagRepo.count());
        assertEquals(2 * questsBefore, questProgressionRepo.count());
        assertEquals(2 * charactersBefore, characterRepo.count());
        assertEquals((int) playthroughsBefore, result.created().get("playthroughs"));

        // ----- 4. Remapping -----
        Long importedCampaignId = onlyOther(idsByName(campaignRepo.findAll(), CampaignJpaEntity::getName,
                "RT Campaign", CampaignJpaEntity::getId), campaignId0);
        Long importedPageId = onlyOther(idsByName(pageRepo.findAll(), PageJpaEntity::getTitle,
                "RT Page", PageJpaEntity::getId), pageId0);
        Long importedPtId = onlyOther(idsByName(playthroughRepo.findAll(), PlaythroughJpaEntity::getName,
                "RT Partie", PlaythroughJpaEntity::getId), ptId0);
        Long importedChapterAId = onlyOther(idsByName(chapterRepo.findAll(), ChapterJpaEntity::getName,
                "RT Chapter A", ChapterJpaEntity::getId), chapterAId0);

        // 4a. Prep : l'arc importé pointe la page importée (pas l'originale).
        ArcJpaEntity importedArc = arcRepo.findAll().stream()
                .filter(a -> "RT Arc".equals(a.getName()) && importedCampaignId.equals(a.getCampaignId()))
                .findFirst().orElseThrow();
        assertTrue(importedArc.getRelatedPageIds().contains(String.valueOf(importedPageId)));
        assertFalse(importedArc.getRelatedPageIds().contains(String.valueOf(pageId0)));

        // 4b. Jeu : la feuille de perso importée pointe la Partie importée (playthroughId préservé, pas null).
        CharacterJpaEntity importedHero = characterRepo.findAll().stream()
                .filter(c -> "RT Hero".equals(c.getName()) && importedPtId.equals(c.getPlaythroughId()))
                .findFirst().orElseThrow();
        assertNotNull(importedHero);

        // 4c. Jeu : la séance importée pointe la Partie importée ; la progression importée le chapitre importé.
        SessionJpaEntity importedSession = sessionRepo.findAll().stream()
                .filter(s -> "RT Séance".equals(s.getName()) && importedPtId.equals(s.getPlaythroughId()))
                .findFirst().orElseThrow();
        // La ref faible String campaignId de la séance est remappée vers la campagne importée.
        assertEquals(String.valueOf(importedCampaignId), importedSession.getCampaignId());
        // L'entrée de journal importée pointe la séance importée (ref faible String sessionId).
        SessionEntryJpaEntity importedEntry = sessionEntryRepo.findAll().stream()
                .filter(e -> String.valueOf(importedSession.getId()).equals(e.getSessionId()))
                .findFirst().orElseThrow();
        assertEquals("Début", importedEntry.getContent());

        // 4d. Quête (Niveau 1) : dupliquée + références remappées (nœud chapitre, page liée).
        assertEquals(2 * questEntitiesBefore, questRepo.count());
        QuestJpaEntity importedQuestEntity = questRepo.findAll().stream()
                .filter(q -> "RT Quest".equals(q.getName()) && importedCampaignId.equals(q.getCampaignId()))
                .findFirst().orElseThrow();
        assertEquals(1, importedQuestEntity.getNodes().size());
        assertEquals(String.valueOf(importedChapterAId), importedQuestEntity.getNodes().get(0).nodeId(),
                "Le nœud CHAPTER de la quête doit pointer le chapitre importé.");
        assertTrue(importedQuestEntity.getRelatedPageIds().contains(String.valueOf(importedPageId)),
                "relatedPageIds de la quête remappé vers la page importée.");
        assertEquals(1, importedQuestEntity.getPrerequisites().size());

        // 4e. quest_progression importée pointe la QUÊTE importée (remap v2 via questMap).
        QuestProgressionJpaEntity importedQuestProg = questProgressionRepo.findAll().stream()
                .filter(q -> importedPtId.equals(q.getPlaythroughId()))
                .findFirst().orElseThrow();
        assertEquals(importedQuestEntity.getId(), importedQuestProg.getQuestId());

        // 4f. Battlemaps : la liste étiquetée survit au round-trip (originale ET importée —
        // le binaire est réutilisé par clé, la ref StoredFile reste valide telle quelle).
        List<SceneJpaEntity> rtScenes = sceneRepo.findAll().stream()
                .filter(s -> "RT Scene".equals(s.getName())).toList();
        assertEquals(2, rtScenes.size());
        for (SceneJpaEntity s : rtScenes) {
            assertEquals(1, s.getBattlemaps().size(), "1 battlemap attendue sur " + s.getId());
            assertEquals("Nuit", s.getBattlemaps().get(0).label());
        }

        // 4f. Horloge importée : pointe la Partie importée + valeurs (segments/filled) préservées.
        ClockJpaEntity importedClock = clockRepo.findAll().stream()
                .filter(c -> "RT Horloge".equals(c.getName()) && importedPtId.equals(c.getPlaythroughId()))
                .findFirst().orElseThrow();
        assertEquals(6, importedClock.getSegments());
        assertEquals(2, importedClock.getFilled());
        // Trigger QUEST_COMPLETED : le triggerRef (id de quête) est remappé vers la quête importée.
        assertEquals(ClockTrigger.QUEST_COMPLETED, importedClock.getTriggerType());
        assertEquals(String.valueOf(importedQuestEntity.getId()), importedClock.getTriggerRef());
        // 4g. Front importé + horloge rattachée à ce front (frontId remappé).
        FrontJpaEntity importedFront = frontRepo.findAll().stream()
                .filter(f -> "RT Front".equals(f.getName()) && importedPtId.equals(f.getPlaythroughId()))
                .findFirst().orElseThrow();
        assertEquals(importedFront.getId(), importedClock.getFrontId());
    }

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

    /** Ids des entités dont le libellé == name. */
    private static <E> List<Long> idsByName(List<E> all, java.util.function.Function<E, String> nameOf,
                                            String name, java.util.function.Function<E, Long> idOf) {
        return all.stream().filter(e -> name.equals(nameOf.apply(e))).map(idOf).toList();
    }

    /** L'unique id de la liste différent de {@code original} (l'élément importé). */
    private static Long onlyOther(List<Long> ids, Long original) {
        return ids.stream().filter(id -> !id.equals(original)).findFirst().orElseThrow();
    }
}
