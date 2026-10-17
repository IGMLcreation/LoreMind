package com.loremind.infrastructure.transfer.pdf;

import com.loremind.infrastructure.persistence.entity.*;
import com.loremind.infrastructure.persistence.jpa.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Mode plat du livret : un seul arc d'un seul chapitre n'est présenté « à plat » que si
 * ces deux conteneurs sont vides. Un chapitre écrit (méthode guidée…) garde ses en-têtes.
 */
@SpringBootTest
@Transactional
class PdfExportFlatModeTest {

    @Autowired private PdfExportService service;
    @Autowired private CampaignJpaRepository campaignRepo;
    @Autowired private ArcJpaRepository arcRepo;
    @Autowired private ChapterJpaRepository chapterRepo;
    @Autowired private SceneJpaRepository sceneRepo;

    private String campaign(String chapterGmNotes) {
        CampaignJpaEntity camp = campaignRepo.save(CampaignJpaEntity.builder().name("C").arcsCount(1).build());
        ArcJpaEntity arc = arcRepo.save(ArcJpaEntity.builder().name("Arc principal").campaignId(camp.getId()).order(0).build());
        ChapterJpaEntity ch = chapterRepo.save(ChapterJpaEntity.builder().name("L'héritage empoisonné")
                .arcId(arc.getId()).order(0).gmNotes(chapterGmNotes).build());
        sceneRepo.save(SceneJpaEntity.builder().name("L'arrestation").chapterId(ch.getId()).order(0).build());
        return String.valueOf(camp.getId());
    }

    @Test
    void bareArcAndChapter_areFlattened() {
        String x = service.xhtml(campaign(null), PdfExportOptions.defaults());
        assertTrue(x.contains("id=\"part-narrative\">Scènes</h1>"));
        assertFalse(x.contains("L'héritage empoisonné</div>"), "en-tête de chapitre masqué");
    }

    @Test
    void writtenChapter_keepsItsHeaders() {
        String x = service.xhtml(campaign("Le notaire est coupable."), PdfExportOptions.defaults());
        assertTrue(x.contains("id=\"part-narrative\">Structure narrative</h1>"));
        assertTrue(x.contains("L'héritage empoisonné"), "le chapitre de la méthode guidée reste visible");
        assertTrue(x.contains("Le notaire est coupable."));
    }
}
