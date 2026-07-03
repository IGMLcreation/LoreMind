package com.loremind.infrastructure.persistence.migration;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.ApplicationRunner;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Migration one-shot des données existantes vers le modèle Playthrough.
 *
 * <p>Idempotente : si une campagne a déjà au moins un Playthrough, on ne crée pas
 * de Partie par défaut pour elle. Les opérations sur les sessions / characters /
 * flags / progression sont toutes filtrées sur l'absence de {@code playthrough_id}
 * pour ne rien dupliquer.</p>
 *
 * <p>Étapes :
 *   1. Crée un Playthrough "Partie principale" pour chaque Campaign qui n'en a pas
 *   2. Renseigne {@code sessions.playthrough_id} depuis l'ancien {@code campaign_id}
 *   3. Renseigne {@code characters.playthrough_id} depuis l'ancien {@code campaign_id}
 *   4. Copie {@code campaign_flag} → {@code playthrough_flag} (si tables présentes)
 *   5. Copie {@code chapters.progression_status} (≠ NOT_STARTED) → {@code quest_progression}
 *
 * <p>S'exécute au démarrage, après Hibernate ddl-auto=update qui aura créé les nouvelles tables.</p>
 */
@Configuration
public class PlaythroughMigrationRunner {

    private static final Logger LOG = LoggerFactory.getLogger(PlaythroughMigrationRunner.class);

    private static final String DEFAULT_PLAYTHROUGH_NAME = "Partie principale";

    @Bean
    public ApplicationRunner runPlaythroughMigration(JdbcTemplate jdbc) {
        return args -> migrate(jdbc);
    }

    @Transactional
    void migrate(JdbcTemplate jdbc) {
        // Garde-fou : si la table playthroughs n'existe pas (anciens dumps), on saute.
        if (!tableExists(jdbc, "playthroughs")) {
            LOG.warn("Migration Playthrough : table 'playthroughs' absente — Hibernate ne l'a pas créée. Skip.");
            return;
        }

        int createdPlaythroughs = createDefaultPlaythroughs(jdbc);
        int migratedSessions = migrateSessions(jdbc);
        int migratedCharacters = migrateCharacters(jdbc);
        int migratedFlags = migrateFlags(jdbc);
        int migratedProgressions = migrateProgressions(jdbc);
        // Relaxe les NOT NULL legacy : ddl-auto=update n'assouplit pas les contraintes.
        relaxLegacyNotNullConstraints(jdbc);

        if (createdPlaythroughs + migratedSessions + migratedCharacters + migratedFlags + migratedProgressions > 0) {
            LOG.info("Migration Playthrough : {} playthrough(s) créé(s), {} session(s), {} PJ, {} flag(s), {} progression(s)",
                    createdPlaythroughs, migratedSessions, migratedCharacters, migratedFlags, migratedProgressions);
        } else {
            LOG.debug("Migration Playthrough : rien à migrer.");
        }
    }

    private int createDefaultPlaythroughs(JdbcTemplate jdbc) {
        List<Long> campaignsWithoutPlaythrough = jdbc.queryForList(
                "SELECT c.id FROM campaigns c " +
                "WHERE NOT EXISTS (SELECT 1 FROM playthroughs p WHERE p.campaign_id = c.id)",
                Long.class
        );
        if (campaignsWithoutPlaythrough.isEmpty()) return 0;

        LocalDateTime now = LocalDateTime.now();
        for (Long campaignId : campaignsWithoutPlaythrough) {
            jdbc.update(
                    "INSERT INTO playthroughs (campaign_id, name, description, created_at, updated_at) " +
                    "VALUES (?, ?, NULL, ?, ?)",
                    campaignId, DEFAULT_PLAYTHROUGH_NAME, now, now
            );
        }
        return campaignsWithoutPlaythrough.size();
    }

    private int migrateSessions(JdbcTemplate jdbc) {
        if (!columnExists(jdbc, "sessions", "campaign_id")) return 0;
        return jdbc.update(
                "UPDATE sessions s " +
                "SET playthrough_id = (SELECT p.id FROM playthroughs p WHERE p.campaign_id = CAST(s.campaign_id AS BIGINT) LIMIT 1) " +
                "WHERE s.playthrough_id IS NULL AND s.campaign_id IS NOT NULL"
        );
    }

    private int migrateCharacters(JdbcTemplate jdbc) {
        if (!columnExists(jdbc, "characters", "campaign_id")) return 0;
        return jdbc.update(
                "UPDATE characters c " +
                "SET playthrough_id = (SELECT p.id FROM playthroughs p WHERE p.campaign_id = c.campaign_id LIMIT 1) " +
                "WHERE c.playthrough_id IS NULL AND c.campaign_id IS NOT NULL"
        );
    }

    private int migrateFlags(JdbcTemplate jdbc) {
        if (!tableExists(jdbc, "campaign_flag") || !tableExists(jdbc, "playthrough_flag")) return 0;
        // Copie uniquement ce qui n'a pas déjà été copié — on déduplique par (playthrough_id, name)
        return jdbc.update(
                "INSERT INTO playthrough_flag (playthrough_id, name, value) " +
                "SELECT p.id, cf.name, cf.value " +
                "FROM campaign_flag cf " +
                "JOIN playthroughs p ON p.campaign_id = cf.campaign_id " +
                "WHERE NOT EXISTS (" +
                "    SELECT 1 FROM playthrough_flag pf " +
                "    WHERE pf.playthrough_id = p.id AND pf.name = cf.name" +
                ")"
        );
    }

    int migrateProgressions(JdbcTemplate jdbc) {
        if (!columnExists(jdbc, "chapters", "progression_status")) return 0;
        if (!tableExists(jdbc, "quest_progression")) return 0;
        // On copie les chapitres dont la progression était != NOT_STARTED, vers la Partie
        // principale de la campagne du chapitre. Niveau 1 : la colonne cible est quest_id
        // (renommée par V11). L'id du chapitre == id de la quête (continuité d'id, décision D4).
        // Déduplique via NOT EXISTS sur (playthrough_id, quest_id).
        return jdbc.update(
                "INSERT INTO quest_progression (playthrough_id, quest_id, status) " +
                "SELECT p.id, ch.id, ch.progression_status " +
                "FROM chapters ch " +
                "JOIN arcs a ON a.id = ch.arc_id " +
                "JOIN playthroughs p ON p.campaign_id = a.campaign_id " +
                "WHERE ch.progression_status IS NOT NULL " +
                "  AND ch.progression_status <> 'NOT_STARTED' " +
                "  AND NOT EXISTS (" +
                "      SELECT 1 FROM quest_progression qp " +
                "      WHERE qp.playthrough_id = p.id AND qp.quest_id = ch.id" +
                "  )"
        );
    }

    /**
     * Relâche les NOT NULL hérités des colonnes désormais optionnelles :
     *   sessions.campaign_id, characters.campaign_id, chapters.progression_status
     * Idempotent : PostgreSQL ne bronche pas si la colonne est déjà NULLABLE.
     */
    private void relaxLegacyNotNullConstraints(JdbcTemplate jdbc) {
        relaxNotNull(jdbc, "sessions", "campaign_id");
        relaxNotNull(jdbc, "characters", "campaign_id");
        relaxNotNull(jdbc, "chapters", "progression_status");
    }

    private void relaxNotNull(JdbcTemplate jdbc, String table, String column) {
        if (!columnExists(jdbc, table, column)) return;
        try {
            jdbc.execute("ALTER TABLE " + table + " ALTER COLUMN " + column + " DROP NOT NULL");
        } catch (DataAccessException ex) {
            // Déjà NULLABLE ou table en cours de migration : log et continue.
            LOG.debug("relaxNotNull({}.{}) : déjà nullable ou non-applicable", table, column);
        }
    }

    private boolean tableExists(JdbcTemplate jdbc, String table) {
        try {
            Integer n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.tables WHERE table_name = ?",
                    Integer.class, table
            );
            return n != null && n > 0;
        } catch (DataAccessException ex) {
            return false;
        }
    }

    private boolean columnExists(JdbcTemplate jdbc, String table, String column) {
        try {
            Integer n = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns WHERE table_name = ? AND column_name = ?",
                    Integer.class, table, column
            );
            return n != null && n > 0;
        } catch (DataAccessException ex) {
            return false;
        }
    }
}
