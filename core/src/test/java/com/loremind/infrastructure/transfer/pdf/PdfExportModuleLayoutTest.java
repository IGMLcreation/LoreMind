package com.loremind.infrastructure.transfer.pdf;

import com.loremind.domain.campaigncontext.structure.LinkType;
import com.loremind.domain.campaigncontext.structure.SceneBattlemap;
import com.loremind.domain.campaigncontext.structure.SceneBranch;
import com.loremind.domain.campaigncontext.structure.SceneType;
import com.loremind.domain.files.ports.FileStorage;
import com.loremind.infrastructure.persistence.entity.*;
import com.loremind.infrastructure.persistence.jpa.*;
import com.loremind.infrastructure.transfer.pdf.PdfExportOptions.LoreScope;
import com.loremind.infrastructure.transfer.pdf.PdfExportOptions.PageFormat;
import com.loremind.infrastructure.transfer.pdf.PdfExportOptions.Section;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.common.PDRectangle;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.when;

/**
 * Mise en page « module » : renvois « (p. N) » vers les scènes et les fiches, renvois morts
 * neutralisés, annexes MJ (révélations et indices, index PNJ et adversaires avec leurs
 * apparitions) et cartes en pleine page paysage.
 */
@SpringBootTest
@Transactional
class PdfExportModuleLayoutTest {

    @Autowired private PdfExportService service;
    @Autowired private CampaignJpaRepository campaignRepo;
    @Autowired private ArcJpaRepository arcRepo;
    @Autowired private ChapterJpaRepository chapterRepo;
    @Autowired private SceneJpaRepository sceneRepo;
    @Autowired private NpcJpaRepository npcRepo;
    @Autowired private EnemyJpaRepository enemyRepo;
    @Autowired private StoredFileJpaRepository storedFileRepo;

    @MockitoBean private FileStorage fileStorage;

    private String campaignId;
    private Long revId;
    private Long enemyId;
    private Long npcId;

    @BeforeEach
    void seed() throws Exception {
        byte[] png = png(300, 200);
        StoredFileJpaEntity map = storedFileRepo.save(StoredFileJpaEntity.builder()
                .filename("map.png").contentType("image/png").sizeBytes(png.length).storageKey("files/layout-map.png").build());
        when(fileStorage.download("files/layout-map.png")).thenAnswer(inv -> new ByteArrayInputStream(png));

        CampaignJpaEntity camp = campaignRepo.save(CampaignJpaEntity.builder().name("Module").arcsCount(1).build());
        campaignId = String.valueOf(camp.getId());
        enemyId = enemyRepo.save(EnemyJpaEntity.builder().campaignId(camp.getId()).name("Goule").level("3")
                .folder("Morts-vivants").order(0).build()).getId();
        npcId = npcRepo.save(NpcJpaEntity.builder().campaignId(camp.getId()).name("Maëlis").folder("Valfroide")
                .order(0).build()).getId();

        ArcJpaEntity arc = arcRepo.save(ArcJpaEntity.builder().name("Acte I").campaignId(camp.getId()).order(0).build());
        ChapterJpaEntity ch = chapterRepo.save(ChapterJpaEntity.builder().name("La foire").arcId(arc.getId()).order(0).build());
        chapterRepo.save(ChapterJpaEntity.builder().name("La crypte").arcId(arc.getId()).order(1).build());

        revId = sceneRepo.save(SceneJpaEntity.builder().name("Le secret du stand").chapterId(ch.getId()).order(2)
                .type(SceneType.REVELATION).build()).getId();
        sceneRepo.save(SceneJpaEntity.builder().name("L'auberge").chapterId(ch.getId()).order(0)
                .branches(new ArrayList<>(List.of(
                        new SceneBranch("Fouiller le stand", String.valueOf(revId), null, LinkType.CLUE),
                        SceneBranch.of("Vers nulle part", "999999"))))
                .build());
        sceneRepo.save(SceneJpaEntity.builder().name("L'embuscade").chapterId(ch.getId()).order(1)
                .type(SceneType.ENCOUNTER).combatDifficulty("Moyenne")
                .enemyIds(new ArrayList<>(List.of(String.valueOf(enemyId))))
                .battlemaps(new ArrayList<>(List.of(new SceneBattlemap("Nuit", String.valueOf(map.getId()), null))))
                .branches(new ArrayList<>(List.of(SceneBranch.of("Le carnet", String.valueOf(revId)))))
                .build());
    }

    private static PdfExportOptions options(boolean gm, Section... sections) {
        return new PdfExportOptions(gm, PageFormat.A4, false,
                sections.length == 0 ? EnumSet.allOf(Section.class) : EnumSet.of(sections[0], sections), LoreScope.ALL);
    }

    @Test
    void sceneExitsAndEnemies_pointToTheirPage() {
        String x = service.xhtml(campaignId, PdfExportOptions.defaults());

        String rev = "#scene-" + revId;
        assertTrue(x.contains("<a class=\"xref\" href=\"" + rev + "\">Le secret du stand</a>"), "nom de scène cliquable");
        assertTrue(x.contains("<a class=\"pref\" href=\"" + rev + "\"></a>"), "numéro de page de la scène");
        assertTrue(x.contains("<a class=\"xref\" href=\"#enemy-" + enemyId + "\">Goule</a>"), "adversaire renvoyé à sa fiche");
        assertTrue(x.contains("id=\"enemy-" + enemyId + "\""), "fiche du bestiaire ancrée");
        assertTrue(x.contains("id=\"npc-" + npcId + "\""), "fiche PNJ ancrée");
    }

    @Test
    void deadReferences_areReducedToPlainText() {
        String x = service.xhtml(campaignId, PdfExportOptions.defaults());
        assertFalse(x.contains("#scene-999999"), "lien vers une scène inexistante neutralisé");

        // Sans bestiaire : le nom de l'adversaire reste, sans lien ni page.
        String noBestiary = service.xhtml(campaignId, options(true, Section.NARRATIVE));
        assertFalse(noBestiary.contains("#enemy-" + enemyId));
        assertTrue(noBestiary.contains("Goule"));
    }

    @Test
    void gmAppendix_listsRevelationSourcesAndIndexes() {
        String x = service.xhtml(campaignId, PdfExportOptions.defaults());

        assertTrue(x.contains("id=\"part-annex\""));
        assertTrue(x.contains("Révélations et indices"));
        int table = x.indexOf("id=\"annex-revelations\"");
        String annex = x.substring(table);
        assertTrue(annex.contains(">L'auberge</a>"), "source de la révélation listée");
        assertTrue(annex.contains(">L'embuscade</a>"));
        assertTrue(annex.contains("indice"), "nature du lien");
        assertTrue(annex.contains("Index des PNJ"));
        assertTrue(annex.contains("Index des adversaires"));
        assertTrue(annex.contains("Morts-vivants"));
    }

    @Test
    void playerEdition_hasNoGmAppendix() {
        String x = service.xhtml(campaignId, options(false));
        assertFalse(x.contains("id=\"part-annex\""));
        assertFalse(x.contains("Révélations et indices"));
    }

    @Test
    void mapsAppendix_replacesInlineBattlemaps() {
        String withMaps = service.xhtml(campaignId, PdfExportOptions.defaults());
        assertTrue(withMaps.contains("class=\"map-page\""));
        assertTrue(withMaps.contains("L'embuscade — Nuit"));
        assertFalse(withMaps.contains("Battlemap — Nuit"), "pas de doublon en petit dans la scène");

        String inline = service.xhtml(campaignId, options(true, Section.NARRATIVE));
        assertFalse(inline.contains("class=\"map-page\""));
        assertTrue(inline.contains("Battlemap — Nuit"), "sans annexe, la carte reste dans la scène");
    }

    @Test
    void mapPages_areLandscape() throws Exception {
        byte[] pdf = service.export(campaignId, PdfExportOptions.defaults());
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDRectangle last = doc.getPage(doc.getNumberOfPages() - 1).getMediaBox();
            assertTrue(last.getWidth() > last.getHeight(), "la carte est sur une page paysage");
            PDRectangle first = doc.getPage(0).getMediaBox();
            assertTrue(first.getWidth() < first.getHeight(), "le reste du livret reste en portrait");
        }
    }

    private static byte[] png(int w, int h) throws Exception {
        BufferedImage img = new BufferedImage(w, h, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
