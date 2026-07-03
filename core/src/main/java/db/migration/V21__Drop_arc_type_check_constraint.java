package db.migration;

import org.flywaydb.core.api.migration.BaseJavaMigration;
import org.flywaydb.core.api.migration.Context;

import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;

/**
 * Supprime la contrainte CHECK {@code type in ('LINEAR','HUB')} héritée du baseline V1
 * sur {@code arcs.type} : elle rejetterait le nouveau type {@code SYSTEM} (arc technique
 * « Quêtes libres », conteneurs des quêtes hors arc).
 *
 * <p>Migration en Java car le NOM de la contrainte n'est pas portable : inline dans le
 * baseline, il est auto-généré différemment par H2 (CONSTRAINT_xxx) et Postgres
 * (arcs_type_check) — et différent encore sur les bases historiques créées par
 * ddl-auto puis baselinées. On la retrouve donc par sa CLAUSE via
 * {@code information_schema.check_constraints} ('LINEAR' n'apparaît dans aucune autre
 * contrainte du schéma). No-op si aucune contrainte ne matche.</p>
 *
 * <p>Pas de re-création avec la liste étendue : même politique que les enums ajoutés
 * après le baseline (scene_type V13, clocks V15…), qui vivent sans CHECK — la valeur
 * est bornée par l'enum Java.</p>
 */
public class V21__Drop_arc_type_check_constraint extends BaseJavaMigration {

    @Override
    public void migrate(Context context) throws Exception {
        Connection conn = context.getConnection();
        List<String> names = new ArrayList<>();
        try (Statement st = conn.createStatement();
             ResultSet rs = st.executeQuery(
                     "SELECT constraint_name FROM information_schema.check_constraints " +
                     "WHERE check_clause LIKE '%LINEAR%'")) {
            while (rs.next()) {
                names.add(rs.getString(1));
            }
        }
        try (Statement st = conn.createStatement()) {
            for (String name : names) {
                st.executeUpdate("ALTER TABLE arcs DROP CONSTRAINT \"" + name + "\"");
            }
        }
    }
}
