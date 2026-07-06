package com.loremind.infrastructure.transfer;

import com.loremind.domain.campaigncontext.quest.NodeType;
import com.loremind.domain.campaigncontext.quest.Prerequisite;
import com.loremind.infrastructure.persistence.entity.ChapterJpaEntity;
import com.loremind.infrastructure.persistence.entity.QuestJpaEntity;
import com.loremind.infrastructure.persistence.entity.QuestProgressionJpaEntity;
import com.loremind.infrastructure.persistence.jpa.ArcJpaRepository;
import com.loremind.infrastructure.persistence.jpa.CampaignJpaRepository;
import com.loremind.infrastructure.persistence.jpa.ChapterJpaRepository;
import com.loremind.infrastructure.persistence.jpa.QuestJpaRepository;
import com.loremind.infrastructure.persistence.jpa.QuestProgressionJpaRepository;
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
    @Autowired private ChapterJpaRepository chapterRepo;
    @Autowired private QuestJpaRepository questRepo;
    @Autowired private QuestProgressionJpaRepository questProgressionRepo;

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

    @Test
    void importsLegacyBundleWithoutQuests_convertsHubChaptersToQuests() throws Exception {
        // Bundle "intermédiaire" : a l'espace de jeu (quest_progression) MAIS PAS de champ quests[]
        // (exporté après l'espace de jeu, avant l'entité Quête). Les chapitres HUB (+ leurs
        // prérequis) doivent être convertis en vraies Quests à l'import.
        String dataJson = """
            {
              "manifest": { "formatVersion": 2, "appVersion": "1.0.0-pre", "exportedAt": "2025-06-01T00:00:00Z", "scope": "complète" },
              "campaigns": [
                { "id": 1, "name": "Legacy Camp", "description": "d", "arcsCount": 1, "playerCount": 0, "loreId": null, "gameSystemId": null }
              ],
              "arcs": [
                { "id": 10, "name": "Hub Arc", "description": "d", "campaignId": 1, "order": 0, "type": "HUB",
                  "icon": null, "relatedPageIds": [], "illustrationImageIds": [] }
              ],
              "chapters": [
                { "id": 100, "name": "QA", "description": "d", "arcId": 10, "order": 0,
                  "prerequisitesJson": "[{\\"kind\\":\\"FLAG_SET\\",\\"flagName\\":\\"f\\"}]",
                  "relatedPageIds": [], "illustrationImageIds": [] },
                { "id": 101, "name": "QB", "description": "d", "arcId": 10, "order": 1,
                  "prerequisitesJson": "[{\\"kind\\":\\"QUEST_COMPLETED\\",\\"questId\\":\\"100\\"}]",
                  "relatedPageIds": [], "illustrationImageIds": [] }
              ],
              "playthroughs": [
                { "id": 50, "campaignId": 1, "name": "P", "description": null }
              ],
              "questProgressions": [
                { "id": 1, "playthroughId": 50, "chapterId": 100, "status": "IN_PROGRESS" }
              ]
            }
            """;
        byte[] zip = zipWithDataJson(dataJson);

        ImportResult result = importService.importZip(new ByteArrayInputStream(zip));

        // Les deux chapitres HUB sont convertis en quêtes.
        assertEquals(2, result.created().get("quests"));
        QuestJpaEntity qa = questRepo.findAll().stream().filter(q -> "QA".equals(q.getName())).findFirst().orElseThrow();
        QuestJpaEntity qb = questRepo.findAll().stream().filter(q -> "QB".equals(q.getName())).findFirst().orElseThrow();

        // QA : 1 prérequis (FlagSet) + un nœud CHAPTER vers le chapitre importé.
        assertEquals(1, qa.getPrerequisites().size());
        assertEquals(1, qa.getNodes().size());
        assertEquals(NodeType.CHAPTER, qa.getNodes().get(0).nodeType());
        ChapterJpaEntity importedChapterQA = chapterRepo.findAll().stream()
                .filter(c -> "QA".equals(c.getName())).findFirst().orElseThrow();
        assertEquals(String.valueOf(importedChapterQA.getId()), qa.getNodes().get(0).nodeId());

        // QB : prérequis QuestCompleted remappé vers la QUÊTE QA (et non un chapitre).
        Prerequisite p = qb.getPrerequisites().get(0);
        assertInstanceOf(Prerequisite.QuestCompleted.class, p);
        assertEquals(String.valueOf(qa.getId()), ((Prerequisite.QuestCompleted) p).questId());

        // quest_progression pointe la QUÊTE QA (et non un chapitre).
        assertEquals(1, questProgressionRepo.count());
        QuestProgressionJpaEntity prog = questProgressionRepo.findAll().get(0);
        assertEquals(qa.getId(), prog.getQuestId());
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
