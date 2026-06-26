package com.loremind.infrastructure.transfer.pdf;

import com.loremind.infrastructure.persistence.entity.CampaignJpaEntity;
import com.loremind.infrastructure.persistence.entity.EnemyJpaEntity;
import com.loremind.infrastructure.persistence.entity.NpcJpaEntity;
import com.loremind.infrastructure.persistence.jpa.CampaignJpaRepository;
import com.loremind.infrastructure.persistence.jpa.EnemyJpaRepository;
import com.loremind.infrastructure.persistence.jpa.NpcJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Export PDF d'une campagne : le pipeline XHTML -> openhtmltopdf produit bien un PDF.
 */
@SpringBootTest
@Transactional
class PdfExportServiceTest {

    @Autowired private PdfExportService pdfExportService;
    @Autowired private CampaignJpaRepository campaignRepo;
    @Autowired private EnemyJpaRepository enemyRepo;
    @Autowired private NpcJpaRepository npcRepo;

    @Test
    void exportsCampaignToPdf() {
        CampaignJpaEntity camp = campaignRepo.save(CampaignJpaEntity.builder()
                .name("Les éclats stellaires").description("Une campagne de test & démo <héros>.")
                .arcsCount(0).build());
        npcRepo.save(NpcJpaEntity.builder()
                .campaignId(camp.getId()).name("Azrak").folder("Azrak").order(0)
                .values(Map.of("Notes", "Marchand suspect.\nDeuxième ligne.")).build());
        enemyRepo.save(EnemyJpaEntity.builder()
                .campaignId(camp.getId()).name("Bandit").folder("Foundry/Briarban").order(0)
                .level("3").foundryStats(Map.of("attributes.hp.value", "11")).build());

        byte[] pdf = pdfExportService.export(String.valueOf(camp.getId()));

        assertNotNull(pdf);
        assertTrue(pdf.length > 1000, "le PDF doit avoir un contenu substantiel");
        // Signature de fichier PDF (%PDF-).
        assertEquals("%PDF-", new String(pdf, 0, 5, java.nio.charset.StandardCharsets.US_ASCII));
        assertEquals("Les éclats stellaires", pdfExportService.campaignName(String.valueOf(camp.getId())));
    }
}
