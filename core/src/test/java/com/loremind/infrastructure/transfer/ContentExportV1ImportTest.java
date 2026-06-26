package com.loremind.infrastructure.transfer;

import com.loremind.infrastructure.persistence.jpa.ArcJpaRepository;
import com.loremind.infrastructure.persistence.jpa.CampaignJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.nio.charset.StandardCharsets;
import java.util.zip.ZipEntry;
import java.util.zip.ZipOutputStream;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Rétro-compatibilité : un export AU FORMAT v1 (avant l'ajout de l'espace de jeu et du
 * champ {@code scope}) doit toujours s'importer. On forge le {@code data.json} v1 À LA MAIN
 * (un {@code buildExport} actuel produirait du v2) : Manifest à 3 champs sans {@code scope},
 * AUCUNE des 5 listes de jeu, et une campagne SANS {@code playerCount}. Verrouille à la fois
 * la désérialisation tolérante (Jackson : champs absents → null) et le {@code nullSafe} en
 * bout de chaîne d'import.
 */
@SpringBootTest
@Transactional
class ContentExportV1ImportTest {

    @Autowired private ImportService importService;
    @Autowired private CampaignJpaRepository campaignRepo;
    @Autowired private ArcJpaRepository arcRepo;

    @Test
    void importsLegacyV1ArchiveWithoutPlaySectionOrScope() throws Exception {
        String dataJson = """
            {
              "manifest": { "formatVersion": 1, "appVersion": "0.9.0", "exportedAt": "2025-01-01T00:00:00Z" },
              "campaigns": [
                { "id": 1, "name": "V1 Camp", "description": "ancienne", "arcsCount": 1, "loreId": null, "gameSystemId": null }
              ],
              "arcs": [
                { "id": 10, "name": "V1 Arc", "description": "d", "campaignId": 1, "order": 0, "type": "LINEAR",
                  "icon": null, "themes": null, "stakes": null, "gmNotes": null, "rewards": null, "resolution": null,
                  "relatedPageIds": [], "illustrationImageIds": [] }
              ]
            }
            """;
        byte[] zip = zipWithDataJson(dataJson);

        long campaignsBefore = campaignRepo.count();
        long arcsBefore = arcRepo.count();

        ImportResult result = importService.importZip(new ByteArrayInputStream(zip));

        // L'archive v1 s'importe sans exception ; son contenu est recréé et remappé.
        assertEquals(campaignsBefore + 1, campaignRepo.count());
        assertEquals(arcsBefore + 1, arcRepo.count());
        assertEquals(1, result.created().get("campaigns"));
        // Les 5 sections de jeu absentes → traitées comme vides (nullSafe), sans planter.
        assertEquals(0, result.created().get("playthroughs"));
        assertEquals(0, result.created().get("sessions"));
        // La campagne v1 (sans playerCount → 0 par défaut) est bien présente.
        assertTrue(campaignRepo.findAll().stream().anyMatch(c -> "V1 Camp".equals(c.getName())));
    }

    private static byte[] zipWithDataJson(String dataJson) throws Exception {
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try (ZipOutputStream zip = new ZipOutputStream(baos)) {
            zip.putNextEntry(new ZipEntry("data.json"));
            zip.write(dataJson.getBytes(StandardCharsets.UTF_8));
            zip.closeEntry();
        }
        return baos.toByteArray();
    }
}
