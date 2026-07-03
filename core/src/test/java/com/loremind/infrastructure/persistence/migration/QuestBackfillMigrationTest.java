package com.loremind.infrastructure.persistence.migration;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.configuration.FluentConfiguration;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Harnais de migration (Niveau 1, Phase 0) — exigé par la critique adverse : la
 * suite normale tourne en {@code ddl-auto=create-drop} avec Flyway DÉSACTIVÉ, donc
 * AUCUN script de migration n'y est exécuté. Ce test rejoue les VRAIES migrations
 * Flyway (V1→V10) sur H2 en {@code MODE=PostgreSQL;NON_KEYWORDS=VALUE} — exactement
 * le moteur de la distribution desktop — pour valider le backfill V10 :
 * <ul>
 *   <li>prédicat aligné sur la règle runtime (HUB OU porte des prérequis OU déjà
 *       référencé par une progression) — y compris un chapitre LINÉAIRE conditionnel ;</li>
 *   <li>zéro perte (toute progression existante reste rattachable) ;</li>
 *   <li>continuité d'id (Quest.id == Chapter.id) et prérequis préservés verbatim ;</li>
 *   <li>anti-collision : une Quest créée APRÈS la migration obtient un id frais.</li>
 * </ul>
 */
class QuestBackfillMigrationTest {

    private String url;
    private Connection keepAlive; // garde la base in-mem vivante pour toute la durée du test

    @BeforeEach
    void setUp() throws Exception {
        // Base unique par exécution + MODE/flags identiques au profil desktop (application-local.properties).
        url = "jdbc:h2:mem:questmig_" + UUID.randomUUID().toString().replace("-", "")
                + ";MODE=PostgreSQL;DB_CLOSE_DELAY=-1;NON_KEYWORDS=VALUE";
        keepAlive = DriverManager.getConnection(url, "sa", "");
    }

    @AfterEach
    void tearDown() throws Exception {
        if (keepAlive != null) keepAlive.close();
    }

    @Test
    void backfills_quests_from_quest_like_chapters_only() throws Exception {
        // 1) Schéma jusqu'à l'état pré-Niveau 1 (V1..V8).
        flyway().target("8").load().migrate();

        // 2) Seed d'un état réaliste.
        seed();

        // 3) Applique V9 (table quests) + V10 (backfill).
        flyway().load().migrate();

        // 4) Vérifications.
        List<Long> questIds = questIds();
        assertEquals(List.of(100L, 101L, 102L, 104L), questIds,
                "Doivent devenir des quêtes : 100 (HUB+prereq), 101 (HUB), 102 (LINÉAIRE+prereq), "
                        + "104 (LINÉAIRE sans prereq mais référencé par une progression). PAS 103 (LINÉAIRE nu).");

        assertFalse(questIds.contains(103L), "Un chapitre linéaire SANS prérequis ni progression n'est pas une quête.");

        // Continuité d'id + champs préservés.
        assertEquals(1L, scalarLong("SELECT campaign_id FROM quests WHERE id = 100"));
        assertEquals("Ch HUB prereq", scalarString("SELECT name FROM quests WHERE id = 100"));
        String prereq100 = scalarString("SELECT prerequisites FROM quests WHERE id = 100");
        assertTrue(prereq100.contains("FLAG_SET") && prereq100.contains("f1"),
                "Les prérequis du chapitre doivent être copiés verbatim sur la quête : " + prereq100);

        // Le nœud d'origine (chapitre) est référencé.
        String nodes100 = scalarString("SELECT nodes FROM quests WHERE id = 100");
        assertTrue(nodes100.contains("\"nodeType\":\"CHAPTER\"") && nodes100.contains("\"nodeId\":\"100\""),
                "La quête doit référencer son chapitre d'origine comme premier nœud : " + nodes100);

        // Cas critique : chapitre LINÉAIRE à prérequis (déjà une quête fonctionnelle aujourd'hui).
        String prereq102 = scalarString("SELECT prerequisites FROM quests WHERE id = 102");
        assertTrue(prereq102.contains("SESSION_REACHED"), "Le chapitre linéaire conditionnel doit être migré : " + prereq102);

        // V11 : quest_progression a basculé chapter_id -> quest_id sans perte (continuité d'id).
        assertEquals(List.of(100L, 104L), longList("SELECT quest_id FROM quest_progression ORDER BY quest_id"),
                "Les progressions seedées (chapitres 100 et 104) doivent pointer quest_id = ancien chapter_id.");

        // 5) Anti-collision : une Quest créée après la migration ne réutilise pas un id backfillé.
        long newId = insertFreshQuestAndReturnId();
        assertTrue(newId > 104L, "Une Quest créée après le backfill doit obtenir un id frais (> max backfillé), obtenu : " + newId);
    }

    // ─────────────────────────── helpers ───────────────────────────

    private FluentConfiguration flyway() {
        return Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration");
    }

    private void seed() throws Exception {
        exec("INSERT INTO campaigns (id, arcs_count, name, created_at, updated_at) "
                + "VALUES (1, 2, 'C', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        exec("INSERT INTO arcs (id, \"order\", campaign_id, type, name, created_at, updated_at) "
                + "VALUES (10, 1, 1, 'HUB', 'Hub Arc', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        exec("INSERT INTO arcs (id, \"order\", campaign_id, type, name, created_at, updated_at) "
                + "VALUES (11, 2, 1, 'LINEAR', 'Linear Arc', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

        // 100 : HUB + prérequis
        exec("INSERT INTO chapters (id, \"order\", arc_id, name, prerequisites, created_at, updated_at) "
                + "VALUES (100, 1, 10, 'Ch HUB prereq', '[{\"kind\":\"FLAG_SET\",\"flagName\":\"f1\"}]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        // 101 : HUB sans prérequis ([])
        exec("INSERT INTO chapters (id, \"order\", arc_id, name, prerequisites, created_at, updated_at) "
                + "VALUES (101, 2, 10, 'Ch HUB', '[]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        // 102 : LINÉAIRE + prérequis (= déjà une quête fonctionnelle)
        exec("INSERT INTO chapters (id, \"order\", arc_id, name, prerequisites, created_at, updated_at) "
                + "VALUES (102, 1, 11, 'Ch LIN prereq', '[{\"kind\":\"SESSION_REACHED\",\"minSessionNumber\":2}]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        // 103 : LINÉAIRE nu (PAS une quête)
        exec("INSERT INTO chapters (id, \"order\", arc_id, name, prerequisites, created_at, updated_at) "
                + "VALUES (103, 2, 11, 'Ch LIN nu', '[]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        // 104 : LINÉAIRE nu MAIS référencé par une progression
        exec("INSERT INTO chapters (id, \"order\", arc_id, name, created_at, updated_at) "
                + "VALUES (104, 3, 11, 'Ch LIN progressed', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");

        exec("INSERT INTO playthroughs (id, campaign_id, name, created_at, updated_at) "
                + "VALUES (50, 1, 'P', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)");
        exec("INSERT INTO quest_progression (playthrough_id, chapter_id, status) VALUES (50, 100, 'COMPLETED')");
        exec("INSERT INTO quest_progression (playthrough_id, chapter_id, status) VALUES (50, 104, 'IN_PROGRESS')");
    }

    private void exec(String sql) throws Exception {
        try (Statement st = keepAlive.createStatement()) {
            st.executeUpdate(sql);
        }
    }

    private List<Long> questIds() throws Exception {
        return longList("SELECT id FROM quests ORDER BY id");
    }

    private List<Long> longList(String sql) throws Exception {
        List<Long> ids = new ArrayList<>();
        try (Statement st = keepAlive.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            while (rs.next()) ids.add(rs.getLong(1));
        }
        return ids;
    }

    private long scalarLong(String sql) throws Exception {
        try (Statement st = keepAlive.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), "Aucun résultat pour : " + sql);
            return rs.getLong(1);
        }
    }

    private String scalarString(String sql) throws Exception {
        try (Statement st = keepAlive.createStatement();
             ResultSet rs = st.executeQuery(sql)) {
            assertTrue(rs.next(), "Aucun résultat pour : " + sql);
            return rs.getString(1);
        }
    }

    private long insertFreshQuestAndReturnId() throws Exception {
        try (PreparedStatement ps = keepAlive.prepareStatement(
                "INSERT INTO quests (campaign_id, \"order\", name, created_at, updated_at) "
                        + "VALUES (1, 1, 'New', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)",
                Statement.RETURN_GENERATED_KEYS)) {
            ps.executeUpdate();
            try (ResultSet rs = ps.getGeneratedKeys()) {
                assertTrue(rs.next(), "Pas de clé générée pour la nouvelle Quest");
                return rs.getLong(1);
            }
        }
    }
}
