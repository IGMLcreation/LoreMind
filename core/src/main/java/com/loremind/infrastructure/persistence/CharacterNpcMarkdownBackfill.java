package com.loremind.infrastructure.persistence;

import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.context.event.ApplicationReadyEvent;
import org.springframework.context.event.EventListener;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Component;

import java.util.Map;

/**
 * Backfill one-shot des fiches Character / Npc post-refonte 2026-04-30.
 * <p>
 * Avant la refonte, les fiches stockaient leur contenu dans la colonne
 * {@code markdown_content}. Apres la refonte, le contenu est dans
 * {@code field_values} (JSON Map<String,String>). La colonne
 * {@code markdown_content} subsiste car Hibernate ddl-auto=update ne drop pas.
 * <p>
 * Ce backfill copie {@code markdown_content} dans {@code field_values["Notes"]}
 * pour toutes les fiches qui ont un markdown non vide ET un field_values vide.
 * Idempotent : si field_values contient deja des donnees, on ne touche pas.
 * <p>
 * La colonne {@code markdown_content} n'est PAS supprimee apres backfill —
 * permet un rollback applicatif au cas ou. Suppression definitive a faire dans
 * une release ulterieure quand la confiance est etablie.
 */
@Component
public class CharacterNpcMarkdownBackfill {

    private static final Logger log = LoggerFactory.getLogger(CharacterNpcMarkdownBackfill.class);

    private final JdbcTemplate jdbc;
    private final ObjectMapper mapper = new ObjectMapper();

    public CharacterNpcMarkdownBackfill(JdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    @EventListener(ApplicationReadyEvent.class)
    public void backfillIfNeeded() {
        if (!hasMarkdownContentColumn("characters")) {
            log.debug("Backfill skip : colonne markdown_content absente (deja migre ou install propre).");
            return;
        }
        int chars = backfillTable("characters");
        int npcs = backfillTable("npcs");
        if (chars + npcs > 0) {
            log.info("Backfill markdown -> field_values : {} character(s), {} npc(s) migre(s).", chars, npcs);
        }
    }

    private boolean hasMarkdownContentColumn(String table) {
        try {
            Integer count = jdbc.queryForObject(
                    "SELECT COUNT(*) FROM information_schema.columns "
                            + "WHERE table_name = ? AND column_name = 'markdown_content'",
                    Integer.class, table);
            return count != null && count > 0;
        } catch (Exception e) {
            log.warn("Backfill : impossible de verifier la colonne markdown_content sur {}: {}",
                    table, e.getMessage());
            return false;
        }
    }

    private int backfillTable(String table) {
        // Selection : fiches avec markdown non vide ET field_values vide ou absent.
        // field_values peut etre NULL (legacy avant refonte) ou "{}" (refonte appliquee mais sans data).
        String selectSql = "SELECT id, markdown_content FROM " + table
                + " WHERE markdown_content IS NOT NULL "
                + "   AND markdown_content <> '' "
                + "   AND (field_values IS NULL OR field_values = '' OR field_values = '{}')";

        var rows = jdbc.queryForList(selectSql);
        int migrated = 0;
        for (var row : rows) {
            Long id = ((Number) row.get("id")).longValue();
            String markdown = (String) row.get("markdown_content");
            String json;
            try {
                json = mapper.writeValueAsString(Map.of("Notes", markdown));
            } catch (Exception e) {
                log.error("Backfill {} id={} : echec serialisation JSON, ignore. {}", table, id, e.getMessage());
                continue;
            }
            jdbc.update("UPDATE " + table + " SET field_values = ? WHERE id = ?", json, id);
            migrated++;
        }
        return migrated;
    }
}
