package com.loremind.infrastructure.transfer.pdf;

import com.loremind.domain.campaigncontext.NodeType;
import com.loremind.domain.campaigncontext.Prerequisite;
import com.loremind.domain.campaigncontext.QuestNodeRef;
import com.loremind.domain.campaigncontext.SceneBattlemap;
import com.loremind.domain.files.ports.FileStorage;
import com.loremind.domain.images.ports.ImageStorage;
import com.loremind.domain.shared.template.FieldType;
import com.loremind.domain.shared.template.TemplateField;
import com.loremind.infrastructure.persistence.entity.*;
import com.loremind.infrastructure.persistence.jpa.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

/**
 * Export PDF — couverture des sections RICHES non exercées par {@link PdfExportServiceTest} :
 * structure narrative (arc → quête → scène), battlemap, lore (pages groupées par chemin de
 * dossier + champs pilotés par template TEXT/KEY_VALUE/IMAGE/TABLE), portraits, et
 * statistiques Foundry nettoyées. Le stockage binaire est mocké pour fournir un VRAI PNG
 * (encode/redimensionnement réellement exécuté) et des cas dégradés (binaire illisible, vidéo).
 */
@SpringBootTest
@Transactional
class PdfExportServiceRichTest {

    @Autowired private PdfExportService service;
    @Autowired private CampaignJpaRepository campaignRepo;
    @Autowired private GameSystemJpaRepository gameSystemRepo;
    @Autowired private ArcJpaRepository arcRepo;
    @Autowired private ChapterJpaRepository chapterRepo;
    @Autowired private SceneJpaRepository sceneRepo;
    @Autowired private QuestJpaRepository questRepo;
    @Autowired private NpcJpaRepository npcRepo;
    @Autowired private EnemyJpaRepository enemyRepo;
    @Autowired private ImageJpaRepository imageRepo;
    @Autowired private StoredFileJpaRepository storedFileRepo;
    @Autowired private LoreJpaRepository loreRepo;
    @Autowired private LoreNodeJpaRepository loreNodeRepo;
    @Autowired private PageJpaRepository pageRepo;
    @Autowired private TemplateJpaRepository templateRepo;

    @MockitoBean private ImageStorage imageStorage;
    @MockitoBean private FileStorage fileStorage;

    @Test
    void exportsRichCampaign_narrativeLorePersonasAndImages() throws Exception {
        byte[] png = tinyPng();
        // Image décodable vs binaire corrompu (branche encode -> null).
        ImageJpaEntity good = imageRepo.save(ImageJpaEntity.builder()
                .filename("good.png").contentType("image/png").sizeBytes(png.length).storageKey("images/good.png").build());
        ImageJpaEntity bad = imageRepo.save(ImageJpaEntity.builder()
                .filename("bad.png").contentType("image/png").sizeBytes(3).storageKey("images/bad.png").build());
        when(imageStorage.download(eq("images/good.png"))).thenAnswer(inv -> new ByteArrayInputStream(png));
        when(imageStorage.download(eq("images/bad.png"))).thenAnswer(inv -> new ByteArrayInputStream("xxx".getBytes()));

        // Battlemap image (rendue) vs vidéo (ignorée car non rendable en PDF).
        StoredFileJpaEntity mapImg = storedFileRepo.save(StoredFileJpaEntity.builder()
                .filename("map.png").contentType("image/png").sizeBytes(png.length).storageKey("files/map.png").build());
        StoredFileJpaEntity clip = storedFileRepo.save(StoredFileJpaEntity.builder()
                .filename("clip.mp4").contentType("video/mp4").sizeBytes(10).storageKey("files/clip.mp4").build());
        when(fileStorage.download(eq("files/map.png"))).thenAnswer(inv -> new ByteArrayInputStream(png));

        // Système de jeu : templates PNJ (TEXT + KEY_VALUE_LIST + IMAGE) et ennemi (TEXT).
        GameSystemJpaEntity gs = gameSystemRepo.save(GameSystemJpaEntity.builder()
                .name("RichSys")
                .npcTemplate(List.of(
                        new TemplateField("Apparence", FieldType.TEXT),
                        TemplateField.keyValueList("Caractéristiques", List.of("Force", "Dextérité")),
                        new TemplateField("Galerie", FieldType.IMAGE)))
                .enemyTemplate(List.of(new TemplateField("Tactique", FieldType.TEXT)))
                .build());

        // Lore : arbre Géographie / Villes + page pilotée par template (TEXT + TABLE).
        LoreJpaEntity lore = loreRepo.save(LoreJpaEntity.builder().name("Atlas").build());
        Long lid = lore.getId();
        LoreNodeJpaEntity geo = loreNodeRepo.save(LoreNodeJpaEntity.builder().name("Géographie").loreId(lid).order(0).build());
        LoreNodeJpaEntity villes = loreNodeRepo.save(LoreNodeJpaEntity.builder().name("Villes").loreId(lid).parentId(geo.getId()).order(0).build());
        TemplateJpaEntity pageTpl = templateRepo.save(TemplateJpaEntity.builder()
                .loreId(lid).name("Ville")
                .fields(List.of(
                        new TemplateField("Histoire", FieldType.TEXT),
                        new TemplateField("Commerces", FieldType.TABLE, null, List.of("Nom", "Prix"))))
                .build());
        pageRepo.save(PageJpaEntity.builder()
                .loreId(lid).nodeId(villes.getId()).templateId(pageTpl.getId()).title("Padhrad").order(0)
                .values(Map.of("Histoire", "Vieille cité portuaire.\nFondée il y a mille ans."))
                .tableValues(Map.of("Commerces", List.of(Map.of("Nom", "Forge de Korr", "Prix", "10 po"))))
                .build());

        CampaignJpaEntity camp = campaignRepo.save(CampaignJpaEntity.builder()
                .name("Campagne Riche & <test>").description("Desc.\nMultiligne.").arcsCount(1)
                .gameSystemId(String.valueOf(gs.getId())).loreId(String.valueOf(lid)).build());

        // Narration : arc -> quête -> 2 scènes (battlemap image + battlemap vidéo).
        ArcJpaEntity arc = arcRepo.save(ArcJpaEntity.builder()
                .name("Acte I").campaignId(camp.getId()).order(0)
                .description("Le voyage commence.").themes("Trahison").stakes("La cité tombe")
                .rewards("Trésor").resolution("Victoire").gmNotes("Secret MJ")
                .illustrationImageIds(List.of(String.valueOf(good.getId()))).build());
        ChapterJpaEntity chap = chapterRepo.save(ChapterJpaEntity.builder()
                .name("La porte").arcId(arc.getId()).order(0)
                .description("Franchir la porte.").playerObjectives("Entrer").narrativeStakes("Le temps presse")
                .gmNotes("Piège").build());
        // 2e chapitre : la campagne n'est PLUS "à plat" -> exerce le rendu HIÉRARCHIQUE (arc -> chapitre -> scènes).
        chapterRepo.save(ChapterJpaEntity.builder()
                .name("La fuite").arcId(arc.getId()).order(1).description("S'échapper.").build());
        sceneRepo.save(SceneJpaEntity.builder()
                .name("L'embuscade").chapterId(chap.getId()).order(0)
                .location("Ruelle").timing("Nuit").atmosphere("Tendue").playerNarration("Des ombres bougent")
                .gmSecretNotes("3 bandits").choicesConsequences("Fuir ou combattre").combatDifficulty("Moyen")
                .battlemaps(List.of(new SceneBattlemap("Nuit", String.valueOf(mapImg.getId()), null))).build());
        sceneRepo.save(SceneJpaEntity.builder()
                .name("La poursuite").chapterId(chap.getId()).order(1)
                .battlemaps(List.of(new SceneBattlemap("", String.valueOf(clip.getId()), null))).build());

        // Quête (Niveau 1) : prérequis (flag) + nœud vers un chapitre + champs narratifs.
        // Exerce la section « Quêtes » du PDF (renderPrerequisites / renderQuestNodes).
        questRepo.save(QuestJpaEntity.builder()
                .campaignId(camp.getId()).name("Sauver le marchand").order(0)
                .description("Retrouver le marchand disparu.")
                .prerequisites(List.of(new Prerequisite.FlagSet("porte_ouverte")))
                .nodes(List.of(new QuestNodeRef(NodeType.CHAPTER, String.valueOf(chap.getId()), 0)))
                .playerObjectives("Le ramener vivant").narrativeStakes("Sa famille attend")
                .gmNotes("Il est retenu par les bandits").build());

        // PNJ avec portrait + champs de template (dont galerie d'image corrompue) ; rangé en dossier.
        npcRepo.save(NpcJpaEntity.builder()
                .campaignId(camp.getId()).name("Maître Orlin").folder("Padhrad").order(0)
                .portraitImageId(String.valueOf(good.getId()))
                .values(Map.of("Apparence", "Vieillard à barbe blanche"))
                .keyValueValues(Map.of("Caractéristiques", Map.of("Force", "8", "Dextérité", "11")))
                .imageValues(Map.of("Galerie", List.of(String.valueOf(bad.getId()))))
                .build());

        // Ennemi avec stats Foundry : bruit (0/false/rollMode) filtré, clés humanisées.
        enemyRepo.save(EnemyJpaEntity.builder()
                .campaignId(camp.getId()).name("Bandit").folder("Pègre").order(0).level("2")
                .values(Map.of("Tactique", "Embuscade"))
                .foundryStats(new java.util.LinkedHashMap<>(Map.of(
                        "attributes.hp.value", "25",
                        "attributes.ac.value", "0",      // bruit (0) -> masqué
                        "system.details.cr", "3",
                        "flags.core.rollMode", "gmroll"  // technique -> masqué
                )))
                .build());

        byte[] pdf = service.export(String.valueOf(camp.getId()));

        assertNotNull(pdf);
        assertTrue(pdf.length > 1000, "le PDF riche doit avoir un contenu substantiel");
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.US_ASCII));
        assertEquals("Campagne Riche & <test>", service.campaignName(String.valueOf(camp.getId())));
    }

    @Test
    void exportsFlatCampaign_scenesWithoutArcChapterHeaders() {
        // Mode plat : 1 arc d'un SEUL chapitre -> les scènes sont présentées à plat
        // (exerce narrativeFlat ; pas d'en-têtes Arc/Chapitre « Quête »).
        CampaignJpaEntity camp = campaignRepo.save(CampaignJpaEntity.builder()
                .name("Campagne plate").description("d").arcsCount(1).build());
        ArcJpaEntity arc = arcRepo.save(ArcJpaEntity.builder()
                .name("Arc masqué").campaignId(camp.getId()).order(0).build());
        ChapterJpaEntity ch = chapterRepo.save(ChapterJpaEntity.builder()
                .name("Chapitre 1").arcId(arc.getId()).order(0).build());
        sceneRepo.save(SceneJpaEntity.builder()
                .name("Scène libre").chapterId(ch.getId()).order(0).playerNarration("Du texte.").build());

        byte[] pdf = service.export(String.valueOf(camp.getId()));

        assertNotNull(pdf);
        assertTrue(pdf.length > 1000, "le PDF plat doit avoir un contenu substantiel");
        assertEquals("%PDF-", new String(pdf, 0, 5, StandardCharsets.US_ASCII));
    }

    @Test
    void export_unknownCampaign_throws() {
        assertThrows(java.util.NoSuchElementException.class, () -> service.export("999999999"));
    }

    /** Minuscule PNG 4×4 réellement décodable par ImageIO (pour exercer encode()). */
    private static byte[] tinyPng() throws Exception {
        BufferedImage img = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        img.setRGB(0, 0, 0x8A7BC8);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(img, "png", out);
        return out.toByteArray();
    }
}
