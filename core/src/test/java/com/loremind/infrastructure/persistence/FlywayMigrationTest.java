package com.loremind.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.MigrationVersion;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Valide que la CHAINE de migrations Flyway (V1 baseline + V2 battlemaps) s'applique
 * proprement sur H2 en mode PostgreSQL — exactement la configuration du desktop
 * local-first ({@code MODE=PostgreSQL}), et au plus pres de la prod Postgres.
 * <p>
 * Necessaire car la suite @SpringBootTest DESACTIVE Flyway (schema via ddl-auto
 * create-drop). Sans ce test, le SQL des migrations ne serait jamais execute par
 * la CI, alors qu'il pilote le schema des vraies installations.
 */
class FlywayMigrationTest {

    // Meme cocktail d'options que application-local.properties (desktop H2).
    private static final String URL =
            "jdbc:h2:mem:flyway_v2_test;MODE=PostgreSQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1";

    @Test
    void migrationsApplyCleanly_onH2PostgresMode() throws SQLException {
        MigrateResult result = Flyway.configure()
                .dataSource(URL, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        // Au moins V1 + V2 jouees sur une base vierge.
        assertTrue(result.migrationsExecuted >= 2,
                "Attendu >= 2 migrations, obtenu " + result.migrationsExecuted);

        try (Connection conn = DriverManager.getConnection(URL, "sa", "");
             Statement st = conn.createStatement()) {

            // V2.1 : la table stored_files existe et est interrogeable.
            st.executeQuery("select id, filename, content_type, size_bytes, storage_key, uploaded_at from stored_files");

            // V22 : la scene porte la LISTE de battlemaps ; la paire V2 a disparu.
            st.executeQuery("select battlemaps from scenes");
            assertThrows(SQLException.class,
                    () -> st.executeQuery("select battlemap_media_file_id from scenes"),
                    "scenes.battlemap_media_file_id aurait du etre supprimee par V22");

            // V2.3 : les anciennes colonnes "cartes / plans" ont bien disparu.
            assertThrows(SQLException.class,
                    () -> st.executeQuery("select map_image_ids from scenes"),
                    "scenes.map_image_ids aurait du etre supprimee par V2");
            assertThrows(SQLException.class,
                    () -> st.executeQuery("select map_image_ids from arcs"),
                    "arcs.map_image_ids aurait du etre supprimee par V2");
            assertThrows(SQLException.class,
                    () -> st.executeQuery("select map_image_ids from chapters"),
                    "chapters.map_image_ids aurait du etre supprimee par V2");

            // V21 : le CHECK V1 sur arcs.type est tombe -> le type SYSTEM
            // (arc technique « Quêtes libres ») est accepte.
            st.executeUpdate("insert into campaigns (name, arcs_count, created_at, updated_at) " +
                    "values ('C', 0, now(), now())");
            st.executeUpdate("insert into arcs (name, campaign_id, \"order\", type, created_at, updated_at) " +
                    "select 'Quêtes libres', id, 9999, 'SYSTEM', now(), now() from campaigns");
        }
    }

    @Test
    void v22_backfillsLegacyBattlemapPair_intoJsonList() throws SQLException {
        String url = "jdbc:h2:mem:flyway_v22_test;MODE=PostgreSQL;NON_KEYWORDS=VALUE;DB_CLOSE_DELAY=-1";

        // 1) Schema arrete AVANT V22 (paire de colonnes encore presente)…
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .target(MigrationVersion.fromVersion("21"))
                .load()
                .migrate();

        // 2) …peuple d'une scene AVEC carte (media+sidecar) et d'une scene SANS.
        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement st = conn.createStatement()) {
            st.executeUpdate("insert into campaigns (id, name, arcs_count, created_at, updated_at) " +
                    "values (1, 'C', 0, now(), now())");
            st.executeUpdate("insert into arcs (id, name, campaign_id, \"order\", created_at, updated_at) " +
                    "values (1, 'A', 1, 0, now(), now())");
            st.executeUpdate("insert into chapters (id, name, arc_id, \"order\", created_at, updated_at) " +
                    "values (1, 'Ch', 1, 0, now(), now())");
            st.executeUpdate("insert into scenes (id, name, chapter_id, \"order\", " +
                    "battlemap_media_file_id, battlemap_data_file_id, created_at, updated_at) " +
                    "values (1, 'Avec carte', 1, 0, '100', '101', now(), now())");
            st.executeUpdate("insert into scenes (id, name, chapter_id, \"order\", created_at, updated_at) " +
                    "values (2, 'Sans carte', 1, 1, now(), now())");
        }

        // 3) Fin de la chaine : V22 transforme la paire en liste JSON.
        Flyway.configure()
                .dataSource(url, "sa", "")
                .locations("classpath:db/migration")
                .load()
                .migrate();

        try (Connection conn = DriverManager.getConnection(url, "sa", "");
             Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery("select id, battlemaps from scenes order by id")) {
            rs.next();
            assertEquals("[{\"label\":\"\",\"mediaFileId\":\"100\",\"dataFileId\":\"101\"}]",
                    rs.getString(2));
            rs.next();
            assertEquals("[]", rs.getString(2));
        }
    }
}
