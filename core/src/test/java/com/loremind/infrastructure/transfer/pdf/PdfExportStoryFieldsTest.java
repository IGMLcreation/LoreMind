package com.loremind.infrastructure.transfer.pdf;

import com.loremind.domain.campaigncontext.bestiary.NpcStory;
import com.loremind.domain.campaigncontext.structure.SceneType;
import com.loremind.infrastructure.persistence.entity.*;
import com.loremind.infrastructure.persistence.jpa.*;
import com.loremind.infrastructure.transfer.pdf.PdfExportOptions.LoreScope;
import com.loremind.infrastructure.transfer.pdf.PdfExportOptions.PageFormat;
import com.loremind.infrastructure.transfer.pdf.PdfExportOptions.Section;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.EnumSet;
import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Champs d'écriture dans le livret : histoire cachée et accroches de l'arc, utilité et
 * tactique des scènes, rôle dans l'histoire des PNJ — secrets réservés à la version MJ,
 * signe distinctif visible de tous.
 */
@SpringBootTest
@Transactional
class PdfExportStoryFieldsTest {

    @Autowired private PdfExportService service;
    @Autowired private CampaignJpaRepository campaignRepo;
    @Autowired private ArcJpaRepository arcRepo;
    @Autowired private ChapterJpaRepository chapterRepo;
    @Autowired private SceneJpaRepository sceneRepo;
    @Autowired private NpcJpaRepository npcRepo;

    private String campaignId;

    @BeforeEach
    void seed() {
        CampaignJpaEntity camp = campaignRepo.save(CampaignJpaEntity.builder().name("Récit").arcsCount(1).build());
        campaignId = String.valueOf(camp.getId());
        ArcJpaEntity arc = arcRepo.save(ArcJpaEntity.builder().name("Acte I").campaignId(camp.getId()).order(0)
                .hiddenTruth("Le régent a empoisonné le roi.").hooks("- Un ami disparaît\n- Une prime est offerte").build());
        ChapterJpaEntity ch = chapterRepo.save(ChapterJpaEntity.builder().name("Chap").arcId(arc.getId()).order(0).build());
        chapterRepo.save(ChapterJpaEntity.builder().name("Chap 2").arcId(arc.getId()).order(1).build());
        sceneRepo.save(SceneJpaEntity.builder().name("L'embuscade").chapterId(ch.getId()).order(0)
                .type(SceneType.ENCOUNTER).combatDifficulty("Moyenne")
                .purpose("Montrer que le régent a des hommes partout.")
                .tactics("Ils fuient dès que leur sergent tombe.").build());
        npcRepo.save(NpcJpaEntity.builder().campaignId(camp.getId()).name("Ysolde").order(0)
                .story(new NpcStory("OPPONENT", "Garder le trône.", "Où est caché le prince.", "Tapote sa bague."))
                .build());
    }

    @Test
    void gmEdition_showsEveryStoryField() {
        String x = service.xhtml(campaignId, PdfExportOptions.defaults());
        for (String s : List.of("Histoire cachée", "Le régent a empoisonné le roi.", ">Accroches<", "Une prime est offerte",
                ">Utilité<", "Montrer que le régent a des hommes partout.",
                "Tactique et moral", "Ils fuient dès que leur sergent tombe.",
                ">Opposant<", "Garder le trône.", "Où est caché le prince.", "Tapote sa bague.")) {
            assertTrue(x.contains(s), "absent de la version MJ : " + s);
        }
    }

    @Test
    void playerEdition_keepsOnlyTheMannerism() {
        String x = service.xhtml(campaignId, new PdfExportOptions(false, PageFormat.A4, false,
                EnumSet.allOf(Section.class), LoreScope.ALL));
        assertTrue(x.contains("Tapote sa bague."), "le signe distinctif se voit");
        for (String s : List.of("Le régent a empoisonné le roi.", "Une prime est offerte",
                "Montrer que le régent a des hommes partout.", "Ils fuient dès que leur sergent tombe.",
                ">Opposant<", "Garder le trône.", "Où est caché le prince.")) {
            assertFalse(x.contains(s), "secret présent dans la version joueurs : " + s);
        }
    }
}
