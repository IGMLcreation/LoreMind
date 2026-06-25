package com.loremind.infrastructure.persistence;

import org.flywaydb.core.Flyway;
import org.flywaydb.core.api.output.MigrateResult;
import org.junit.jupiter.api.Test;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.sql.Statement;

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

            // V2.2 : la scene porte la battlemap.
            st.executeQuery("select battlemap_media_file_id, battlemap_data_file_id from scenes");

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
        }
    }
}
