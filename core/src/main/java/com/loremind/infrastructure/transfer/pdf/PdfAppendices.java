package com.loremind.infrastructure.transfer.pdf;

import com.loremind.domain.campaigncontext.structure.LinkType;
import com.loremind.domain.campaigncontext.structure.Room;
import com.loremind.domain.campaigncontext.structure.SceneBranch;
import com.loremind.domain.campaigncontext.structure.SceneType;
import com.loremind.infrastructure.persistence.entity.ArcJpaEntity;
import com.loremind.infrastructure.persistence.entity.ChapterJpaEntity;
import com.loremind.infrastructure.persistence.entity.SceneJpaEntity;

import java.util.*;

import static com.loremind.infrastructure.transfer.pdf.PdfHtml.*;

/**
 * Annexes d'un module publie : ce que le MJ consulte en jeu sans feuilleter tout le livret.
 * <ul>
 *   <li>Revelations et indices : pour chaque revelation, les scenes qui y menent (et leur page) —
 *   le MJ voit d'un coup d'oeil ce que les joueurs peuvent encore decouvrir ;</li>
 *   <li>index des PNJ et des adversaires (avec les scenes ou ces derniers apparaissent) ;</li>
 *   <li>cartes : chaque battlemap et plan de piece sur sa propre page paysage, en grand,
 *   prete a imprimer ou a montrer aux joueurs.</li>
 * </ul>
 * Les renvois vers des parties absentes sont neutralises en fin de rendu ({@link PdfHtml#resolveRefs}).
 */
final class PdfAppendices {

    /** Une carte rendue en pleine page paysage dans l'annexe. */
    record MapPage(String anchor, String title, PdfImage image) {}

    /** Ligne d'index (PNJ ou adversaire). */
    record IndexRow(String anchor, String name, String folder, String level) {}

    /** Resolution des cartes en annexe (plus fine que les illustrations : elles sont imprimees en grand). */
    static final int MAP_MAX = 2400;
    private static final String TD_OPEN = "<td>";
    private static final String TD_CLOSE = "</td>";

    private PdfAppendices() {}

    // ====================================================================== Cartes

    /**
     * Cartes de chaque scene, dans l'ordre du livret : battlemaps (images uniquement) puis
     * plans des pieces. Encodees UNE fois : la scene y renvoie, l'annexe les affiche.
     */
    static Map<Long, List<MapPage>> collectMaps(Structure st, PdfImageEncoder encoder) {
        Map<Long, List<MapPage>> maps = new LinkedHashMap<>();
        for (SceneJpaEntity sc : scenesInOrder(st)) {
            List<MapPage> pages = new ArrayList<>();
            if (sc.getBattlemaps() != null) {
                int i = 0;
                for (var bm : sc.getBattlemaps()) {
                    PdfImage img = encoder.fileImage(bm.mediaFileId(), MAP_MAX);
                    if (img == null) continue;
                    String label = notBlank(bm.label()) ? sc.getName() + " — " + bm.label().trim() : sc.getName();
                    pages.add(new MapPage("map-" + sc.getId() + "-" + (i++), label, img));
                }
            }
            if (sc.getRooms() != null) {
                List<Room> rooms = new ArrayList<>(sc.getRooms());
                rooms.sort(Comparator.comparingInt(Room::getOrder));
                for (Room r : rooms) {
                    PdfImage img = encoder.image(r.getMapImageId(), MAP_MAX);
                    if (img != null) {
                        pages.add(new MapPage(roomMapAnchor(sc.getId(), r.getId()),
                                sc.getName() + " — Plan : " + r.getName(), img));
                    }
                }
            }
            if (!pages.isEmpty()) maps.put(sc.getId(), pages);
        }
        return maps;
    }

    static String roomMapAnchor(Long sceneId, String roomId) {
        return "map-room-" + sceneId + "-" + (roomId == null ? "x" : roomId.replaceAll("[^A-Za-z0-9_-]", ""));
    }

    /**
     * Partie « Cartes » : une carte par page paysage (page nommee {@code landscape}), titre
     * en tete, image la plus grande possible sans deformation.
     */
    static void mapsPart(PdfExportService.Ctx ctx, Map<Long, List<MapPage>> maps) {
        if (maps.isEmpty()) return;
        StringBuilder b = ctx.body;
        PdfExportOptions.PageFormat format = ctx.opt.format();
        // Page paysage : largeur = hauteur du format portrait. Marges 1 cm, ~1,6 cm pour le titre.
        double pageW = format.landscapeWidthCm() - 2.0;
        double pageH = format.widthCm - 2.0 - 1.6;
        StringBuilder bms = new StringBuilder();
        boolean first = true;
        for (List<MapPage> pages : maps.values()) {
            for (MapPage p : pages) {
                b.append("<div class=\"map-page\"");
                if (first) b.append(" id=\"part-maps\"");
                b.append("><div class=\"map-title\" id=\"").append(p.anchor()).append("\">")
                        .append(esc(p.title())).append("</div>");
                double w = Math.min(pageW, pageH * p.image().w() / p.image().h());
                b.append("<img style=\"width:").append(cm(w)).append("\" src=\"").append(p.image().uri()).append("\"/>");
                b.append(DIV_CLOSE);
                bms.append(bookmark(p.title(), p.anchor(), ""));
                first = false;
            }
        }
        ctx.addToc(0, "Cartes", "part-maps");
        ctx.bookmarks.append(bookmark("Cartes", "part-maps", bms.toString()));
    }

    // ====================================================================== Annexes MJ

    /** Revelations et indices : chaque scene REVELATION, et les scenes qui y menent. */
    static void revelations(StringBuilder b, Structure st) {
        List<SceneJpaEntity> scenes = scenesInOrder(st);
        List<SceneJpaEntity> revelations = scenes.stream()
                .filter(s -> s.getType() == SceneType.REVELATION).toList();
        if (revelations.isEmpty()) return;

        b.append("<h2 class=\"section-head\" id=\"annex-revelations\">Révélations et indices</h2>");
        b.append("<p class=\"prose annex-intro\">Pour chaque révélation, les scènes qui permettent de la "
                + "découvrir. Visez au moins trois chemins : les joueurs en manqueront toujours un.</p>");
        b.append("<table class=\"stats-table annex-table\"><thead><tr><th>Révélation</th>"
                + "<th>Y mènent</th></tr></thead><tbody>");
        int row = 0;
        for (SceneJpaEntity rev : revelations) {
            String anchor = "scene-" + rev.getId();
            b.append(trTag(row++)).append(TD_OPEN).append(xref(anchor, rev.getName())).append(' ')
                    .append(pageRef(anchor)).append(TD_CLOSE).append(TD_OPEN);
            List<String> sources = incoming(rev, scenes);
            if (sources.isEmpty()) {
                b.append("<span class=\"annex-warn\">Aucune scène n'y mène.</span>");
            } else {
                b.append("<ul class=\"annex-list\">");
                for (String s : sources) b.append("<li>").append(s).append("</li>");
                b.append("</ul>");
            }
            b.append(TD_TR_CLOSE);
        }
        b.append(TBODY_TABLE_CLOSE);
    }

    /** Scenes (du meme chapitre) qui ont un lien vers {@code target}, en HTML avec renvoi. */
    private static List<String> incoming(SceneJpaEntity target, List<SceneJpaEntity> scenes) {
        String targetId = String.valueOf(target.getId());
        List<String> out = new ArrayList<>();
        for (SceneJpaEntity s : scenes) {
            if (s.getBranches() == null || Objects.equals(s.getId(), target.getId())) continue;
            for (SceneBranch br : s.getBranches()) {
                if (!targetId.equals(br.targetSceneId())) continue;
                String anchor = "scene-" + s.getId();
                StringBuilder line = new StringBuilder(xref(anchor, s.getName())).append(' ').append(pageRef(anchor));
                if (br.kind() == LinkType.CLUE) line.append(" <span class=\"annex-kind\">indice</span>");
                else if (br.kind() == LinkType.LEAD) line.append(" <span class=\"annex-kind\">piste</span>");
                if (notBlank(br.label())) line.append(" — ").append(esc(br.label().trim()));
                out.add(line.toString());
                break; // une ligne par scene source
            }
        }
        return out;
    }

    /** Index alphabetique : nom (renvoi), dossier, niveau eventuel, apparitions eventuelles, page. */
    static void index(StringBuilder b, String anchorId, String title, List<IndexRow> rows,
                      Map<String, List<String>> appearances) {
        if (rows.isEmpty()) return;
        List<IndexRow> sorted = new ArrayList<>(rows);
        sorted.sort(Comparator.comparing(r -> r.name() == null ? "" : r.name().toLowerCase(Locale.ROOT)));
        boolean withLevel = sorted.stream().anyMatch(r -> notBlank(r.level()));
        boolean withAppearances = appearances != null && !appearances.isEmpty();

        b.append("<h2 class=\"section-head\" id=\"").append(anchorId).append("\">").append(esc(title)).append("</h2>");
        b.append("<table class=\"stats-table annex-table\"><thead><tr><th>Nom</th><th>Dossier</th>");
        if (withLevel) b.append("<th>Niv.</th>");
        if (withAppearances) b.append("<th>Apparaît dans</th>");
        b.append("<th class=\"page-col\">Page</th></tr></thead><tbody>");
        int row = 0;
        for (IndexRow r : sorted) {
            b.append(trTag(row++)).append(TD_OPEN).append(xref(r.anchor(), r.name())).append(TD_CLOSE)
                    .append(TD_OPEN).append(esc(notBlank(r.folder()) ? r.folder().replace("/", " / ") : "—")).append(TD_CLOSE);
            if (withLevel) b.append(TD_OPEN).append(esc(notBlank(r.level()) ? r.level() : "—")).append(TD_CLOSE);
            if (withAppearances) {
                List<String> where = appearances.getOrDefault(r.anchor(), List.of());
                b.append(TD_OPEN).append(where.isEmpty() ? "—" : String.join(", ", where)).append(TD_CLOSE);
            }
            b.append("<td class=\"page-col\">").append(pageRef(r.anchor())).append(TD_TR_CLOSE);
        }
        b.append(TBODY_TABLE_CLOSE);
    }

    /**
     * Ou apparait chaque adversaire : ancre de sa fiche ({@code enemy-<id>}) -&gt; scenes
     * (renvois) qui le citent, dans la scene elle-meme ou dans l'une de ses pieces.
     */
    static Map<String, List<String>> enemyAppearances(Structure st) {
        Map<String, Set<Long>> sceneIdsByEnemy = new LinkedHashMap<>();
        Map<Long, SceneJpaEntity> byId = new LinkedHashMap<>();
        for (SceneJpaEntity sc : scenesInOrder(st)) {
            byId.put(sc.getId(), sc);
            List<String> ids = new ArrayList<>();
            if (sc.getEnemyIds() != null) ids.addAll(sc.getEnemyIds());
            if (sc.getRooms() != null) {
                for (Room r : sc.getRooms()) if (r.getEnemyIds() != null) ids.addAll(r.getEnemyIds());
            }
            for (String id : ids) {
                if (notBlank(id)) sceneIdsByEnemy.computeIfAbsent("enemy-" + id.trim(), k -> new LinkedHashSet<>()).add(sc.getId());
            }
        }
        Map<String, List<String>> out = new LinkedHashMap<>();
        sceneIdsByEnemy.forEach((enemyAnchor, sceneIds) -> {
            List<String> refs = new ArrayList<>();
            for (Long sid : sceneIds) {
                String anchor = "scene-" + sid;
                refs.add(xref(anchor, byId.get(sid).getName()) + " " + pageRef(anchor));
            }
            out.put(enemyAnchor, refs);
        });
        return out;
    }

    /** Toutes les scenes dans l'ordre du livret (arcs, chapitres, scenes). */
    private static List<SceneJpaEntity> scenesInOrder(Structure st) {
        List<SceneJpaEntity> out = new ArrayList<>();
        for (ArcJpaEntity arc : st.arcs) {
            for (ChapterJpaEntity ch : st.chaptersByArc.getOrDefault(arc.getId(), List.of())) {
                out.addAll(st.scenesByChapter.getOrDefault(ch.getId(), List.of()));
            }
        }
        return out;
    }
}
