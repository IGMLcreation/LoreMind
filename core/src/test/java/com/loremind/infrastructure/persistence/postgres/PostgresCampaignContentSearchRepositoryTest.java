package com.loremind.infrastructure.persistence.postgres;

import com.loremind.application.campaigncontext.CampaignContentSearchService;
import com.loremind.domain.campaigncontext.CampaignContentHit;
import com.loremind.infrastructure.web.controller.CampaignContentSearchController;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabase;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseBuilder;
import org.springframework.jdbc.datasource.embedded.EmbeddedDatabaseType;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import java.util.List;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

/** Isolated SQL/API tests: never connects to the user's database. */
class PostgresCampaignContentSearchRepositoryTest {
    private EmbeddedDatabase database;
    private JdbcTemplate jdbc;
    private CampaignContentSearchService service;

    @BeforeEach
    void setup() {
        database = new EmbeddedDatabaseBuilder().generateUniqueName(true).setType(EmbeddedDatabaseType.H2).build();
        jdbc = new JdbcTemplate(database);
        jdbc.execute("SET MODE PostgreSQL");
        jdbc.execute("CREATE TABLE campaigns (id BIGINT PRIMARY KEY, name VARCHAR NOT NULL)");
        jdbc.execute("CREATE TABLE arcs (id BIGINT PRIMARY KEY, name VARCHAR, campaign_id BIGINT, type VARCHAR)");
        jdbc.execute("CREATE TABLE chapters (id BIGINT PRIMARY KEY, name VARCHAR, arc_id BIGINT)");
        jdbc.execute("CREATE TABLE scenes (id BIGINT PRIMARY KEY, name VARCHAR, chapter_id BIGINT)");
        jdbc.execute("CREATE TABLE quests (id BIGINT PRIMARY KEY, name VARCHAR, campaign_id BIGINT, arc_id BIGINT)");
        jdbc.update("INSERT INTO campaigns VALUES (1, 'Les Ombres'), (2, 'Autre campagne')");
        jdbc.update("INSERT INTO arcs VALUES (10, 'Port sombre', 1, 'LINEAR'), (11, 'Technique', 1, 'SYSTEM')");
        jdbc.update("INSERT INTO chapters VALUES (20, 'Nuit sombre', 10), (21, 'Le témoin', 11)");
        jdbc.update("INSERT INTO scenes VALUES (30, 'Taverne sombre', 20), (31, 'Rencontre sombre', 21)");
        jdbc.update("INSERT INTO quests VALUES (40, 'Secret sombre', 1, NULL), (41, 'Secret sombre', 2, NULL)");
        service = new CampaignContentSearchService(new PostgresCampaignContentSearchRepository(new NamedParameterJdbcTemplate(database)));
    }

    @AfterEach
    void cleanup() { database.shutdown(); }

    @Test
    void findsEveryKindWithCorrectAncestorsAndCampaignNames() {
        List<CampaignContentHit> hits = service.search(" SOMBRE ");
        assertEquals(6, hits.size());
        assertEquals(4, hits.stream().map(CampaignContentHit::kind).distinct().count());
        CampaignContentHit scene = hits.stream().filter(h -> h.id().equals("30")).findFirst().orElseThrow();
        assertEquals("1", scene.campaignId());
        assertEquals("Les Ombres", scene.campaignName());
        assertEquals("10", scene.arcId());
        assertEquals("20", scene.chapterId());
        assertEquals("Nuit sombre", scene.chapterName());
        assertTrue(hits.stream().anyMatch(h -> h.id().equals("41") && h.campaignName().equals("Autre campagne")));
    }

    @Test
    void hidesSystemContainersButKeepsTheirScenesNavigable() {
        assertTrue(service.search("Technique").isEmpty());
        assertTrue(service.search("témoin").isEmpty());
        CampaignContentHit hit = service.search("Rencontre").get(0);
        assertEquals("11", hit.arcId());
        assertNull(hit.arcName());
        assertEquals("21", hit.chapterId());
    }

    @Test
    void treatsSqlWildcardsAndQuotesAsLiteralUserText() {
        jdbc.update("INSERT INTO scenes VALUES (32, ?, 20)", "100%_! d'ombre");
        assertEquals(1, service.search("%_").size());
        assertEquals(1, service.search("_!").size());
        assertEquals(1, service.search("d'ombre").size());
        assertTrue(service.search("' OR 1=1 --").isEmpty());
        assertTrue(service.search("  ").isEmpty());
        assertTrue(service.search("s").isEmpty());
        assertTrue(service.search(null).isEmpty());
    }

    @Test
    void excludesOrphansAndBoundsResults() {
        jdbc.update("INSERT INTO scenes VALUES (33, 'Orpheline', 999)");
        assertTrue(service.search("Orpheline").isEmpty());
        for (int i = 100; i < 200; i++) jdbc.update("INSERT INTO quests VALUES (?, ?, 1, NULL)", i, "Recherche " + i);
        assertEquals(80, service.search("Recherche").size());
    }

    @Test
    void exposesSearchProjectionAtTheFrontendEndpoint() throws Exception {
        MockMvcBuilders.standaloneSetup(new CampaignContentSearchController(service)).build()
            .perform(get("/api/campaigns/search-content").param("q", "Taverne"))
            .andExpect(status().isOk())
            .andExpect(jsonPath("$[0].kind").value("scene"))
            .andExpect(jsonPath("$[0].chapterId").value("20"))
            .andExpect(jsonPath("$[0].campaignName").value("Les Ombres"));
    }
}
