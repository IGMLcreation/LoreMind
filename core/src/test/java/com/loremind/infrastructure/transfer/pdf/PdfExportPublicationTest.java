package com.loremind.infrastructure.transfer.pdf;

import com.loremind.domain.campaigncontext.CampaignPublication;
import com.loremind.domain.images.ports.ImageStorage;
import com.loremind.infrastructure.persistence.entity.*;
import com.loremind.infrastructure.persistence.jpa.*;
import com.loremind.infrastructure.transfer.pdf.PdfExportOptions.LoreScope;
import com.loremind.infrastructure.transfer.pdf.PdfExportOptions.PageFormat;
import com.loremind.infrastructure.transfer.pdf.PdfExportOptions.Section;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDDocumentInformation;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Export PDF « module publiable » : fiche Publication (couverture, crédits, introduction,
 * propriétés du fichier), corrections du livret (résumé de scène, libellés accentués),
 * version joueurs sans secrets, choix des parties, lore limité aux pages liées, formats.
 * Le contenu est vérifié sur le XHTML intermédiaire, le fichier sur le PDF réel.
 */
@SpringBootTest
@Transactional
class PdfExportPublicationTest {

    @Autowired private PdfExportService service;
    @Autowired private CampaignJpaRepository campaignRepo;
    @Autowired private ArcJpaRepository arcRepo;
    @Autowired private ChapterJpaRepository chapterRepo;
    @Autowired private SceneJpaRepository sceneRepo;
    @Autowired private LoreJpaRepository loreRepo;
    @Autowired private LoreNodeJpaRepository loreNodeRepo;
    @Autowired private PageJpaRepository pageRepo;

    @MockitoBean private ImageStorage imageStorage;

    private String campaignId;

    @BeforeEach
    void seed() {
        LoreJpaEntity lore = loreRepo.save(LoreJpaEntity.builder().name("Univers").build());
        LoreNodeJpaEntity node = loreNodeRepo.save(LoreNodeJpaEntity.builder()
                .name("Lieux").loreId(lore.getId()).order(0).build());
        PageJpaEntity linked = pageRepo.save(PageJpaEntity.builder()
                .loreId(lore.getId()).nodeId(node.getId()).title("Valfroide").order(0).build());
        pageRepo.save(PageJpaEntity.builder()
                .loreId(lore.getId()).nodeId(node.getId()).title("Secret d'une autre campagne").order(1).build());

        CampaignJpaEntity camp = campaignRepo.save(CampaignJpaEntity.builder()
                .name("Les Cendres de Jais").description("Description interne.").arcsCount(1)
                .loreId(String.valueOf(lore.getId()))
                .publication(new CampaignPublication("Une enquête dans le Nord", "Camille Martin",
                        "Chroniques Oubliées", "1 à 3", "3 à 5", "2 séances de 4 h",
                        "Une tour se rallume.", "Le maître de la tour a survécu.",
                        "- Un colporteur disparaît\n- Une lettre anonyme", "Mort d'un enfant évoquée.",
                        "Relecture : Alex", "Texte sous licence CC BY-NC 4.0.", "1.2", null))
                .build());
        campaignId = String.valueOf(camp.getId());

        ArcJpaEntity arc = arcRepo.save(ArcJpaEntity.builder().name("Acte I").campaignId(camp.getId()).order(0)
                .themes("Trahison").rewards("Or").resolution("Victoire").gmNotes("Note d'arc MJ").build());
        ChapterJpaEntity ch1 = chapterRepo.save(ChapterJpaEntity.builder()
                .name("La foire").arcId(arc.getId()).order(0).playerObjectives("Trouver le colporteur").build());
        chapterRepo.save(ChapterJpaEntity.builder().name("La crypte").arcId(arc.getId()).order(1).build());
        sceneRepo.save(SceneJpaEntity.builder()
                .name("L'auberge").chapterId(ch1.getId()).order(0)
                .description("Les PJ rencontrent Maëlis.")
                .playerNarration("La porte s'ouvre sur une vague de chaleur.")
                .gmSecretNotes("Maëlis ment.").choicesConsequences("Payer une tournée")
                .combatDifficulty("Facile")
                .relatedPageIds(new ArrayList<>(List.of(String.valueOf(linked.getId()))))
                .build());
    }

    @Test
    void gmEdition_rendersPublicationIntroCreditsAndFixedLabels() {
        String x = service.xhtml(campaignId, PdfExportOptions.defaults());

        // Couverture : système en surtitre, sous-titre, ligne technique, auteur, version — pas de date.
        assertTrue(x.contains("cover-kicker\">Chroniques Oubliées"));
        assertTrue(x.contains("cover-subtitle\">Une enquête dans le Nord"));
        assertTrue(x.contains("Niveaux : 1 à 3"));
        assertTrue(x.contains("cover-author\">Camille Martin"));
        assertTrue(x.contains("Version 1.2"));
        assertFalse(x.contains("Exporté le"));
        assertFalse(x.contains("Livret de campagne"));
        // Crédits + mentions légales.
        assertTrue(x.contains("class=\"credits-page\""));
        assertTrue(x.contains("Relecture : Alex"));
        assertTrue(x.contains("CC BY-NC 4.0"));
        // Introduction MJ.
        assertTrue(x.contains("L'aventure en bref"));
        assertTrue(x.contains("Une tour se rallume."));
        assertTrue(x.contains("Contexte pour le MJ"));
        assertTrue(x.contains("<li>Un colporteur disparaît</li>"));
        assertTrue(x.contains("Avertissements de contenu"));
        assertTrue(x.contains("Acte I : La foire, La crypte"), "structure de l'aventure");
        // Corrections du livret : résumé de scène rendu, libellés accentués.
        assertTrue(x.contains("Résumé"));
        assertTrue(x.contains("Les PJ rencontrent Maëlis."));
        assertTrue(x.contains(">Thèmes<"));
        assertTrue(x.contains(">Récompenses<"));
        assertTrue(x.contains(">Résolution<"));
        assertTrue(x.contains("Choix et conséquences"));
        // Propriétés du fichier.
        assertTrue(x.contains("<title>Les Cendres de Jais</title>"));
        assertTrue(x.contains("<meta name=\"author\" content=\"Camille Martin\"/>"));
        assertTrue(x.contains("lang=\"fr\""));
    }

    @Test
    void playerEdition_dropsEverySecret() {
        PdfExportOptions player = new PdfExportOptions(false, PageFormat.A4, false,
                EnumSet.allOf(Section.class), LoreScope.ALL);
        String x = service.xhtml(campaignId, player);

        assertTrue(x.contains("La porte s'ouvre sur une vague de chaleur."), "le texte à lire reste");
        assertTrue(x.contains("Trouver le colporteur"), "les objectifs joueurs restent");
        assertTrue(x.contains("Édition joueurs"));
        for (String secret : List.of("Maëlis ment.", "Payer une tournée", "Facile", "Les PJ rencontrent Maëlis.",
                "Note d'arc MJ", "Trahison", "Victoire", "Le maître de la tour a survécu.", "Un colporteur disparaît")) {
            assertFalse(x.contains(secret), "secret présent dans la version joueurs : " + secret);
        }
    }

    @Test
    void sectionsAndLinkedLore_limitTheBooklet() {
        String all = service.xhtml(campaignId, PdfExportOptions.defaults());
        assertTrue(all.contains("Secret d'une autre campagne"), "lore complet par défaut");

        PdfExportOptions linked = new PdfExportOptions(true, PageFormat.A4, false,
                EnumSet.of(Section.LORE), LoreScope.LINKED);
        String x = service.xhtml(campaignId, linked);
        assertTrue(x.contains("Valfroide"), "page liée à une scène");
        assertFalse(x.contains("Secret d'une autre campagne"), "page non liée exclue");
        assertFalse(x.contains("id=\"part-narrative\""), "partie narrative non demandée");
        assertFalse(x.contains("Structure de l'aventure"));
    }

    @Test
    void formatAndPrintMode_shapeTheStylesheet() {
        String a5 = service.xhtml(campaignId, new PdfExportOptions(true, PageFormat.A5, true,
                EnumSet.allOf(Section.class), LoreScope.ALL));
        assertTrue(a5.contains("size: A5"));
        assertTrue(a5.contains("font-size: 9.0pt"));
        assertTrue(a5.contains(".arc-head { background: none"), "feuille impression ajoutée");

        String a4 = service.xhtml(campaignId, PdfExportOptions.defaults());
        assertTrue(a4.contains("size: A4"));
        assertFalse(a4.contains(".arc-head { background: none"));
    }

    @Test
    void pdfFile_carriesTitleAndAuthor() throws Exception {
        // Mode impression inclus : sa feuille de style doit rester du XML valide.
        byte[] pdf = service.export(campaignId, new PdfExportOptions(true, PageFormat.LETTER, true,
                EnumSet.allOf(Section.class), LoreScope.ALL));
        try (PDDocument doc = PDDocument.load(pdf)) {
            PDDocumentInformation info = doc.getDocumentInformation();
            assertEquals("Les Cendres de Jais", info.getTitle());
            assertEquals("Camille Martin", info.getAuthor());
            assertEquals("Une enquête dans le Nord", info.getSubject());
            // US Letter : 8,5 pouces de large (612 points).
            assertEquals(612f, doc.getPage(0).getMediaBox().getWidth(), 1f);
        }
    }

    @Test
    void withoutPublication_keepsTheHistoricalBooklet() {
        CampaignJpaEntity bare = campaignRepo.save(CampaignJpaEntity.builder()
                .name("Brouillon").description("Ma description.").arcsCount(0).build());
        String x = service.xhtml(String.valueOf(bare.getId()), PdfExportOptions.defaults());

        assertTrue(x.contains("Livret de campagne"));
        assertTrue(x.contains("cover-desc\">Ma description."));
        assertFalse(x.contains("id=\"part-intro\""));
        assertFalse(x.contains("class=\"credits-page\""));
    }
}
