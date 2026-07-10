package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.util.ArrayList;
import java.util.List;

/**
 * Nettoyage des CONTENEURS ORPHELINS des quêtes libres (arc technique SYSTEM).
 *
 * <p>Jusqu'à la 1.0.2, supprimer une quête libre GARDAIT son chapitre-conteneur
 * s'il contenait des scènes (garde anti-perte de contenu) — mais l'arc SYSTEM
 * étant masqué partout dans l'appli, ce contenu devenait INVISIBLE et
 * irrécupérable, tout en ressortant dans l'export Foundry (« quêtes fantômes »).
 * {@code QuestService.deleteQuest} cascade désormais ces conteneurs ; cette
 * migration répare l'existant SANS perte :</p>
 * <ul>
 *   <li>conteneur orphelin AVEC scènes → une quête libre est RECRÉÉE dessus (même
 *       nom que le chapitre) : le contenu redevient visible sous « Quêtes », et
 *       l'utilisateur le garde ou le supprime via le flux normal (qui annonce
 *       l'impact) ;</li>
 *   <li>conteneur orphelin VIDE → supprimé (fantôme sans contenu).</li>
 * </ul>
 *
 * <p>« Orphelin » = chapitre d'un arc SYSTEM dont l'id n'apparaît dans les
 * {@code nodes} d'AUCUNE quête de la campagne. Détection prudente par motif
 * {@code "nodeId":"<id>"} : une collision d'id avec un nœud SCENE fait considérer
 * le chapitre comme référencé → il est laissé en place (aucun risque de perte).</p>
 *
 * <p>Migration en Java (et non SQL) : la corrélation chapitre ↔ nodes JSON des
 * quêtes demande une itération par candidat, illisible en SQL portable H2/PG.</p>
 */
@SuppressWarnings("java:S101") // Nommage V24__... IMPOSE par la convention Flyway des migrations Java.
public class V24__Reattach_orphan_free_quest_containers extends BaseJavaMigration {

    private record Candidate(long chapterId, String name, long campaignId) {}

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();

        List<Candidate> candidates = new ArrayList<>();
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT c.id, c.name, a.campaign_id FROM chapters c "
                        + "JOIN arcs a ON c.arc_id = a.id WHERE a.type = 'SYSTEM'");
             ResultSet rs = ps.executeQuery()) {
            while (rs.next()) {
                candidates.add(new Candidate(rs.getLong(1), rs.getString(2), rs.getLong(3)));
            }
        }

        for (Candidate ch : candidates) {
            if (isReferencedByAQuest(conn, ch)) continue; // conteneur d'une quête vivante
            if (countScenes(conn, ch.chapterId()) == 0) {
                deleteEmptyChapter(conn, ch.chapterId());
            } else {
                recreateFreeQuest(conn, ch);
            }
        }
    }

    private boolean isReferencedByAQuest(Connection conn, Candidate ch) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM quests WHERE campaign_id = ? AND nodes LIKE ?")) {
            ps.setLong(1, ch.campaignId());
            ps.setString(2, "%\"nodeId\":\"" + ch.chapterId() + "\"%");
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getLong(1) > 0;
            }
        }
    }

    private int countScenes(Connection conn, long chapterId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COUNT(*) FROM scenes WHERE chapter_id = ?")) {
            ps.setLong(1, chapterId);
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                return rs.getInt(1);
            }
        }
    }

    private void deleteEmptyChapter(Connection conn, long chapterId) throws Exception {
        try (PreparedStatement ps = conn.prepareStatement("DELETE FROM chapters WHERE id = ?")) {
            ps.setLong(1, chapterId);
            ps.executeUpdate();
        }
    }

    /** Recrée une quête LIBRE (arc_id NULL) pointant le conteneur, à la suite de l'ordre. */
    private void recreateFreeQuest(Connection conn, Candidate ch) throws Exception {
        int order;
        try (PreparedStatement ps = conn.prepareStatement(
                "SELECT COALESCE(MAX(\"order\"), -1) + 1 FROM quests WHERE campaign_id = ?")) {
            ps.setLong(1, ch.campaignId());
            try (ResultSet rs = ps.executeQuery()) {
                rs.next();
                order = rs.getInt(1);
            }
        }
        try (PreparedStatement ps = conn.prepareStatement(
                "INSERT INTO quests (campaign_id, \"order\", name, description, prerequisites, "
                        + "nodes, related_page_ids, illustration_image_ids, created_at, updated_at) "
                        + "VALUES (?, ?, ?, '', '[]', ?, '[]', '[]', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)")) {
            ps.setLong(1, ch.campaignId());
            ps.setInt(2, order);
            ps.setString(3, ch.name());
            ps.setString(4, "[{\"nodeType\":\"CHAPTER\",\"nodeId\":\"" + ch.chapterId() + "\",\"order\":0}]");
            ps.executeUpdate();
        }
    }
}
