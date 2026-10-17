package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.campaigncontext.CampaignContentHit;
import com.loremind.domain.campaigncontext.ports.CampaignContentSearchRepository;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.stereotype.Repository;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/** One bounded query; never loads complete scenes or one tree per campaign. */
@Repository
public class PostgresCampaignContentSearchRepository implements CampaignContentSearchRepository {
    private final NamedParameterJdbcTemplate jdbc;

    public PostgresCampaignContentSearchRepository(NamedParameterJdbcTemplate jdbc) {
        this.jdbc = jdbc;
    }

    private static final String SQL = """
        SELECT * FROM (
            SELECT 'arc' AS kind, a.id, a.name, c.id AS campaign_id, c.name AS campaign_name,
                   a.id AS arc_id, a.name AS arc_name, CAST(NULL AS BIGINT) AS chapter_id,
                   CAST(NULL AS VARCHAR) AS chapter_name
              FROM arcs a JOIN campaigns c ON c.id = a.campaign_id
             WHERE a.type <> 'SYSTEM' AND LOWER(a.name) LIKE :query ESCAPE '!'
            UNION ALL
            SELECT 'chapter', ch.id, ch.name, c.id, c.name, a.id, a.name, ch.id, ch.name
              FROM chapters ch JOIN arcs a ON a.id = ch.arc_id JOIN campaigns c ON c.id = a.campaign_id
             WHERE a.type <> 'SYSTEM' AND LOWER(ch.name) LIKE :query ESCAPE '!'
            UNION ALL
            SELECT 'scene', s.id, s.name, c.id, c.name, a.id,
                   CASE WHEN a.type = 'SYSTEM' THEN NULL ELSE a.name END, ch.id, ch.name
              FROM scenes s JOIN chapters ch ON ch.id = s.chapter_id
              JOIN arcs a ON a.id = ch.arc_id JOIN campaigns c ON c.id = a.campaign_id
             WHERE LOWER(s.name) LIKE :query ESCAPE '!'
            UNION ALL
            SELECT 'quest', q.id, q.name, c.id, c.name, a.id, a.name, NULL, NULL
              FROM quests q JOIN campaigns c ON c.id = q.campaign_id
              LEFT JOIN arcs a ON a.id = q.arc_id AND a.campaign_id = c.id
             WHERE LOWER(q.name) LIKE :query ESCAPE '!'
        ) hits
        ORDER BY LOWER(name), campaign_id, kind, id
        LIMIT 80
        """;

    @Override
    public List<CampaignContentHit> searchByName(String query) {
        String pattern = "%" + query.toLowerCase(Locale.ROOT)
                .replace("!", "!!").replace("%", "!%").replace("_", "!_") + "%";
        return jdbc.query(SQL, Map.of("query", pattern), (rs, row) -> new CampaignContentHit(
                rs.getString("kind"), rs.getString("id"), rs.getString("name"),
                rs.getString("campaign_id"), rs.getString("campaign_name"),
                rs.getString("arc_id"), rs.getString("arc_name"),
                rs.getString("chapter_id"), rs.getString("chapter_name")));
    }
}
