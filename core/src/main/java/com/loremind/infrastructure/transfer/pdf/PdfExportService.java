package com.loremind.infrastructure.transfer.pdf;

import com.loremind.domain.campaigncontext.quest.NodeType;
import com.loremind.domain.campaigncontext.quest.Prerequisite;
import com.loremind.domain.campaigncontext.quest.QuestNodeRef;
import com.loremind.domain.campaigncontext.structure.Room;
import com.loremind.domain.campaigncontext.structure.RoomBranch;
import com.loremind.domain.campaigncontext.structure.SceneBranch;
import com.loremind.domain.shared.template.TemplateField;
import com.loremind.infrastructure.persistence.entity.*;
import com.loremind.infrastructure.persistence.jpa.*;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.*;

import static com.loremind.infrastructure.transfer.pdf.PdfHtml.*;

/**
 * Export d'UNE campagne en livret PDF (joli document imprimable) : structure narrative
 * (arcs/chapitres/scenes), quetes, PNJ &amp; ennemis (bestiaire), lore, et battlemaps en
 * illustration.
 * <p>
 * Mise en page "livre de jeu de role" : couverture, sommaire imprime avec numeros de
 * page (target-counter), en-tete courant, corps en serif avec libelles de champs en
 * tete de ligne (style stat-block), caracteristiques en tableau horizontal compact.
 * <p>
 * Le livret reflete la FUSION quete/conteneur de l'arbre (cf. QuestService) : l'arc
 * technique SYSTEM (« Quetes libres ») est masque, un chapitre-conteneur est rendu
 * comme « Quete » avec les champs de sa quete fusionnes, et la partie « Quetes » ne
 * liste que les quetes SANS conteneur dans la narration (libres ou transversales) —
 * pas de doublon avec les arcs/chapitres.
 * <p>
 * Rendu XHTML+CSS -&gt; PDF via openhtmltopdf (100 % JVM). Les images sont rincees,
 * redimensionnees et re-encodees en JPEG (ImageIO + decodeur WebP TwelveMonkeys) puis
 * embarquees en data-URI : garantit le rendu (WebP compris) et borne la taille du PDF.
 */
@Service
public class PdfExportService {

    private static final Logger log = LoggerFactory.getLogger(PdfExportService.class);

    /** Largeur utile d'une page A4 (21cm - 2 x 1.7cm de marges). */
    private static final double CONTENT_WIDTH_CM = 17.6;
    /** Hauteur max affichee d'une illustration : evite qu'une image portrait mange la page. */
    private static final double ILLUSTRATION_MAX_HEIGHT_CM = 11.0;
    /** Hauteur max affichee d'un portrait de fiche (colonne de 3cm de large). */
    private static final double PORTRAIT_MAX_HEIGHT_CM = 4.5;

    private static final DateTimeFormatter FR_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);

    // ----- Libelles/police repetes (S1192 ; fragments HTML generiques dans PdfHtml) -----
    private static final String FONT_SERIF = "DejaVu Serif";
    private static final String FONT_SANS = "DejaVu Sans";
    private static final String ANCHOR_PART_NARRATIVE = "part-narrative";
    private static final String LABEL_DESCRIPTION = "Description";
    private static final String LABEL_OBJECTIFS_JOUEURS = "Objectifs joueurs";
    private static final String LABEL_ENJEUX_NARRATIFS = "Enjeux narratifs";
    private static final String CSS_SECRET = "secret";
    private static final String LABEL_NOTES_MJ = "Notes MJ";
    private static final String LABEL_SANS_DOSSIER = "Sans dossier";

    private final CampaignJpaRepository campaignRepo;
    private final NpcJpaRepository npcRepo;
    private final EnemyJpaRepository enemyRepo;
    private final RandomTableJpaRepository randomTableRepo;
    private final GameSystemJpaRepository gameSystemRepo;
    private final LoreNodeJpaRepository loreNodeRepo;
    private final PageJpaRepository pageRepo;
    private final TemplateJpaRepository templateRepo;
    private final PdfStructureLoader structureLoader;
    private final PdfImageEncoder imageEncoder;

    public PdfExportService(CampaignJpaRepository campaignRepo,
                            NpcJpaRepository npcRepo, EnemyJpaRepository enemyRepo,
                            RandomTableJpaRepository randomTableRepo,
                            GameSystemJpaRepository gameSystemRepo,
                            LoreNodeJpaRepository loreNodeRepo, PageJpaRepository pageRepo,
                            TemplateJpaRepository templateRepo,
                            PdfStructureLoader structureLoader, PdfImageEncoder imageEncoder) {
        this.campaignRepo = campaignRepo;
        this.npcRepo = npcRepo;
        this.enemyRepo = enemyRepo;
        this.randomTableRepo = randomTableRepo;
        this.gameSystemRepo = gameSystemRepo;
        this.loreNodeRepo = loreNodeRepo;
        this.pageRepo = pageRepo;
        this.templateRepo = templateRepo;
        this.structureLoader = structureLoader;
        this.imageEncoder = imageEncoder;
    }

    /** Nom de la campagne (pour le nom de fichier). @throws NoSuchElementException si absente. */
    public String campaignName(String campaignId) {
        return campaign(campaignId).getName();
    }

    /** Construit le PDF complet de la campagne. @throws NoSuchElementException si absente. */
    public byte[] export(String campaignId) {
        CampaignJpaEntity campaign = campaign(campaignId);
        String xhtml = buildXhtml(campaign);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            registerFonts(builder);
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            builder.run();
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Echec de la generation du PDF", e);
        }
    }

    private CampaignJpaEntity campaign(String campaignId) {
        return campaignRepo.findById(Long.parseLong(campaignId))
                .orElseThrow(() -> new NoSuchElementException("Campagne introuvable : " + campaignId));
    }

    // ====================================================================== Polices

    /**
     * Enregistre les polices DejaVu embarquées (resources/fonts, licence libre incluse) :
     * couverture Unicode tres large (fleches, ≈, cyrillique...) et vrais gras/italiques,
     * la ou les polices de base PDF ne couvrent que WinAnsi. Sous-ensemble embarque
     * (subset=true) : seuls les glyphes utilises entrent dans le PDF.
     * Si une ressource manque, le CSS retombe sur serif/sans-serif de base.
     */
    private void registerFonts(PdfRendererBuilder builder) {
        font(builder, "/fonts/DejaVuSerif.ttf", FONT_SERIF, 400, BaseRendererBuilder.FontStyle.NORMAL);
        font(builder, "/fonts/DejaVuSerif-Bold.ttf", FONT_SERIF, 700, BaseRendererBuilder.FontStyle.NORMAL);
        font(builder, "/fonts/DejaVuSerif-Italic.ttf", FONT_SERIF, 400, BaseRendererBuilder.FontStyle.ITALIC);
        font(builder, "/fonts/DejaVuSerif-BoldItalic.ttf", FONT_SERIF, 700, BaseRendererBuilder.FontStyle.ITALIC);
        font(builder, "/fonts/DejaVuSans.ttf", FONT_SANS, 400, BaseRendererBuilder.FontStyle.NORMAL);
        font(builder, "/fonts/DejaVuSans-Bold.ttf", FONT_SANS, 700, BaseRendererBuilder.FontStyle.NORMAL);
        font(builder, "/fonts/DejaVuSans-Oblique.ttf", FONT_SANS, 400, BaseRendererBuilder.FontStyle.ITALIC);
        font(builder, "/fonts/DejaVuSans-BoldOblique.ttf", FONT_SANS, 700, BaseRendererBuilder.FontStyle.ITALIC);
    }

    private void font(PdfRendererBuilder builder, String resource, String family,
                      int weight, BaseRendererBuilder.FontStyle style) {
        if (PdfExportService.class.getResource(resource) == null) {
            log.warn("Police absente du classpath, repli polices de base : {}", resource);
            return;
        }
        builder.useFont(() -> PdfExportService.class.getResourceAsStream(resource), family, weight, style, true);
    }

    // ====================================================================== XHTML

    /** Entree du sommaire imprime (niveau 0 = partie, 1 = arc/dossier/quete, 2 = chapitre). */
    private record TocEntry(int level, String title, String anchor) {}

    /**
     * Contexte de rendu : les sections ecrivent leur corps et alimentent les signets,
     * le sommaire et les compteurs (repris ensuite sur la couverture).
     */
    private static final class Ctx {
        final StringBuilder body = new StringBuilder();
        final StringBuilder bookmarks = new StringBuilder();
        final List<TocEntry> toc = new ArrayList<>();
        int scenes;
        int quests;
        int npcs;
        int enemies;
        int randomTables;
        int lorePages;

        void addToc(int level, String title, String anchor) {
            toc.add(new TocEntry(level, title, anchor));
        }
    }

    private String buildXhtml(CampaignJpaEntity campaign) {
        List<TemplateField> npcTemplate = resolveTemplate(campaign.getGameSystemId(), true);
        List<TemplateField> enemyTemplate = resolveTemplate(campaign.getGameSystemId(), false);

        // Les sections d'abord (elles remplissent sommaire + compteurs), la couverture
        // et le sommaire sont assembles ensuite en tete de document.
        Structure st = structureLoader.load(campaign);
        Ctx ctx = new Ctx();
        ctx.quests = st.quests.size();
        narrative(ctx, st);
        quests(ctx, st);
        personas(ctx, "part-npcs", "Personnages non-joueurs (PNJ)", npcEntries(campaign), npcTemplate, false);
        personas(ctx, "part-enemies", "Bestiaire", enemyEntries(campaign), enemyTemplate, true);
        randomTables(ctx, campaign);
        lore(ctx, campaign);

        StringBuilder full = new StringBuilder();
        cover(full, campaign, ctx);
        full.append(tocHtml(ctx.toc));
        full.append(ctx.body);

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"utf-8\"/>\n"
                + "<style>" + cssFor(campaign.getName()) + "</style>\n"
                + "<bookmarks>" + ctx.bookmarks + "</bookmarks>\n"
                + "</head><body>" + full + "</body></html>";
    }

    private void cover(StringBuilder b, CampaignJpaEntity c, Ctx ctx) {
        b.append("<div class=\"cover\">");
        b.append("<div class=\"subtitle\">Livret de campagne</div>");
        b.append("<h1 class=\"cover-title\">").append(esc(c.getName())).append("</h1>");
        b.append("<div class=\"cover-rule\"></div>");
        String meta = coverMeta(ctx);
        if (!meta.isEmpty()) {
            b.append("<div class=\"cover-meta\">").append(esc(meta)).append(DIV_CLOSE);
        }
        if (notBlank(c.getDescription())) {
            b.append("<div class=\"cover-desc\">").append(multiline(c.getDescription())).append(DIV_CLOSE);
        }
        b.append("<div class=\"cover-date\">Exporté le ")
                .append(esc(LocalDate.now(ZoneId.systemDefault()).format(FR_DATE)))
                .append(DIV_CLOSE);
        b.append(DIV_CLOSE);
    }

    /** Ligne "3 scènes · 2 quêtes · 4 PNJ ..." de la couverture (parties non vides seulement). */
    private static String coverMeta(Ctx ctx) {
        List<String> parts = new ArrayList<>();
        if (ctx.scenes > 0) parts.add(plural(ctx.scenes, "scène", "scènes"));
        if (ctx.quests > 0) parts.add(plural(ctx.quests, "quête", "quêtes"));
        if (ctx.npcs > 0) parts.add(ctx.npcs + " PNJ");
        if (ctx.enemies > 0) parts.add(plural(ctx.enemies, "fiche de bestiaire", "fiches de bestiaire"));
        if (ctx.randomTables > 0) parts.add(plural(ctx.randomTables, "table aléatoire", "tables aléatoires"));
        if (ctx.lorePages > 0) parts.add(plural(ctx.lorePages, "page de lore", "pages de lore"));
        return String.join("  ·  ", parts);
    }

    private static String plural(int n, String one, String many) {
        return n + " " + (n == 1 ? one : many);
    }

    /** Sommaire imprime : titres a gauche, numeros de page a droite (target-counter). */
    private static String tocHtml(List<TocEntry> toc) {
        if (toc.isEmpty()) return "";
        StringBuilder t = new StringBuilder();
        t.append("<div class=\"toc-page\"><h1 class=\"toc-title\">Sommaire</h1><table class=\"toc\">");
        for (TocEntry e : toc) {
            t.append("<tr class=\"lvl").append(e.level()).append("\"><td class=\"t\"><a href=\"#")
                    .append(e.anchor()).append("\">").append(esc(e.title()))
                    .append("</a></td><td class=\"p\"><a href=\"#").append(e.anchor())
                    .append("\"></a></td></tr>");
        }
        t.append("</table></div>");
        return t.toString();
    }

    // ----- Structure narrative : arcs -> chapitres (ou quetes fusionnees) -> scenes -----

    private void narrative(Ctx ctx, Structure st) {
        List<ArcJpaEntity> arcs = st.visibleArcs;
        if (arcs.isEmpty()) return;

        // Mode plat (miroir de l'UI Niveau 0) : 1 arc d'UN SEUL chapitre → on masque les
        // niveaux Arc/Chapitre et on présente les scènes À PLAT (cohérent avec la sidebar).
        if (arcs.size() == 1) {
            List<ChapterJpaEntity> only = st.chaptersByArc.get(arcs.get(0).getId());
            if (only.size() == 1) {
                narrativeFlat(ctx, st, arcs.get(0), only.get(0));
                return;
            }
        }

        ctx.body.append("<h1 class=\"part\" id=\"part-narrative\">Structure narrative</h1>");
        ctx.addToc(0, "Structure narrative", ANCHOR_PART_NARRATIVE);
        narrativeBookmarks(ctx, st, arcs);

        for (ArcJpaEntity arc : arcs) {
            arcSection(ctx, st, arc);
        }
    }

    /** Arborescence des signets Arc -> Chapitre pour la partie narrative complete (mode non plat). */
    private void narrativeBookmarks(Ctx ctx, Structure st, List<ArcJpaEntity> arcs) {
        StringBuilder arcBms = new StringBuilder();
        for (ArcJpaEntity arc : arcs) {
            StringBuilder chBms = new StringBuilder();
            for (ChapterJpaEntity ch : st.chaptersByArc.get(arc.getId())) {
                chBms.append(bookmark(ch.getName(), "ch-" + ch.getId(), ""));
            }
            arcBms.append(bookmark(arc.getName(), "arc-" + arc.getId(), chBms.toString()));
        }
        ctx.bookmarks.append(bookmark("Structure narrative", ANCHOR_PART_NARRATIVE, arcBms.toString()));
    }

    /** Un arc complet : en-tete + champs + tous ses chapitres. */
    private void arcSection(Ctx ctx, Structure st, ArcJpaEntity arc) {
        StringBuilder b = ctx.body;
        ctx.addToc(1, arc.getName(), "arc-" + arc.getId());
        b.append("<div class=\"arc\"><h2 class=\"arc-head\" id=\"arc-").append(arc.getId()).append("\">")
                .append("<span class=\"eyebrow eyebrow-light\">Arc</span>")
                .append(esc(arc.getName())).append("</h2>");
        illustrations(b, arc.getIllustrationImageIds());
        block(b, LABEL_DESCRIPTION, arc.getDescription());
        block(b, "Themes", arc.getThemes());
        block(b, "Enjeux", arc.getStakes());
        block(b, "Recompenses", arc.getRewards());
        block(b, "Resolution", arc.getResolution());
        box(b, CSS_SECRET, LABEL_NOTES_MJ, arc.getGmNotes());

        for (ChapterJpaEntity ch : st.chaptersByArc.get(arc.getId())) {
            chapterSection(ctx, st, ch);
        }
        b.append(DIV_CLOSE);
    }

    /** Un chapitre narratif complet (dans son arc) : en-tete + champs (fusionnes ou non) + scenes. */
    private void chapterSection(Ctx ctx, Structure st, ChapterJpaEntity ch) {
        StringBuilder b = ctx.body;
        QuestJpaEntity fused = st.questByContainerChapter.get(ch.getId());
        ctx.addToc(2, ch.getName(), "ch-" + ch.getId());
        b.append("<div class=\"chapter\"><div class=\"chapter-head\" id=\"ch-").append(ch.getId()).append("\">")
                .append("<span class=\"eyebrow\">").append(fused != null ? "Quête" : "Chapitre").append(SPAN_CLOSE)
                .append(esc(ch.getName())).append(DIV_CLOSE);
        chapterFields(b, ch, fused, st);

        for (SceneJpaEntity sc : st.scenesByChapter.get(ch.getId())) {
            scene(b, sc, st);
            ctx.scenes++;
        }
        b.append(DIV_CLOSE);
    }

    /**
     * Champs d'un chapitre ; si une quete est FUSIONNEE dessus (chapitre-conteneur), ses
     * champs sont integres au meme bloc — conditions de deblocage comprises — en dedupliquant
     * les textes identiques (les quetes converties du legacy dupliquent le chapitre).
     */
    private void chapterFields(StringBuilder b, ChapterJpaEntity ch, QuestJpaEntity fused, Structure st) {
        illustrations(b, ch.getIllustrationImageIds());
        if (fused == null) {
            block(b, LABEL_DESCRIPTION, ch.getDescription());
            block(b, LABEL_OBJECTIFS_JOUEURS, ch.getPlayerObjectives());
            block(b, LABEL_ENJEUX_NARRATIFS, ch.getNarrativeStakes());
            box(b, CSS_SECRET, LABEL_NOTES_MJ, ch.getGmNotes());
            return;
        }
        illustrations(b, fused.getIllustrationImageIds());
        block(b, LABEL_DESCRIPTION, mergeField(ch.getDescription(), fused.getDescription()));
        block(b, "Conditions de déblocage", renderPrerequisites(fused.getPrerequisites(), st.questNames));
        block(b, "Nœuds liés", renderQuestNodes(fused, st));
        block(b, LABEL_OBJECTIFS_JOUEURS, mergeField(ch.getPlayerObjectives(), fused.getPlayerObjectives()));
        block(b, LABEL_ENJEUX_NARRATIFS, mergeField(ch.getNarrativeStakes(), fused.getNarrativeStakes()));
        box(b, CSS_SECRET, LABEL_NOTES_MJ, mergeField(ch.getGmNotes(), fused.getGmNotes()));
    }

    /** Deux sources pour un meme champ : garde la non-vide, ou les deux si differentes. */
    private static String mergeField(String chapterValue, String questValue) {
        if (!notBlank(chapterValue)) return questValue;
        if (!notBlank(questValue)) return chapterValue;
        if (chapterValue.trim().equals(questValue.trim())) return chapterValue;
        return chapterValue + "\n\n" + questValue;
    }

    /**
     * Mode plat : scènes présentées sous « Scènes », sans en-têtes Arc/Chapitre. Le contenu
     * narratif éventuel des niveaux masqués reste affiché en intro ({@code block} ignore les
     * valeurs vides → en pratique rien n'apparaît pour un arc/chapitre auto-créés et vides).
     */
    private void narrativeFlat(Ctx ctx, Structure st, ArcJpaEntity arc, ChapterJpaEntity ch) {
        StringBuilder b = ctx.body;
        b.append("<h1 class=\"part\" id=\"part-narrative\">Scènes</h1>");
        ctx.addToc(0, "Scènes", ANCHOR_PART_NARRATIVE);

        illustrations(b, arc.getIllustrationImageIds());
        block(b, LABEL_DESCRIPTION, arc.getDescription());
        block(b, "Themes", arc.getThemes());
        block(b, "Enjeux", arc.getStakes());
        block(b, "Recompenses", arc.getRewards());
        block(b, "Resolution", arc.getResolution());
        box(b, CSS_SECRET, LABEL_NOTES_MJ, arc.getGmNotes());
        chapterFields(b, ch, st.questByContainerChapter.get(ch.getId()), st);

        StringBuilder scBms = new StringBuilder();
        for (SceneJpaEntity sc : st.scenesByChapter.get(ch.getId())) {
            ctx.addToc(1, sc.getName(), "scene-" + sc.getId());
            scBms.append(bookmark(sc.getName(), "scene-" + sc.getId(), ""));
            scene(b, sc, st);
            ctx.scenes++;
        }
        ctx.bookmarks.append(bookmark("Scènes", ANCHOR_PART_NARRATIVE, scBms.toString()));
    }

    private void scene(StringBuilder b, SceneJpaEntity sc, Structure st) {
        b.append("<div class=\"scene\" id=\"scene-").append(sc.getId()).append("\"><div class=\"scene-head\">")
                .append("<span class=\"eyebrow\">Scène</span>")
                .append(esc(sc.getName())).append(DIV_CLOSE);
        sceneMeta(b, sc);
        block(b, "Ambiance", sc.getAtmosphere(), "ambiance");
        box(b, "readaloud", "À lire aux joueurs", sc.getPlayerNarration());
        box(b, CSS_SECRET, "Notes secrètes MJ", sc.getGmSecretNotes());
        block(b, "Choix & consequences", sc.getChoicesConsequences());
        block(b, "Sorties", renderSceneBranches(sc.getBranches(), st));
        // Combat : difficulte + ennemis de la rencontre (refs bestiaire resolues en noms
        // + texte libre) dans le meme encadre rouge.
        box(b, "combat", "Combat", joinNonBlank(sc.getCombatDifficulty(),
                enemyLines(sc.getEnemyIds(), sc.getEnemies(), st)));
        illustrations(b, sc.getIllustrationImageIds());
        sceneBattlemaps(b, sc);
        rooms(b, sc, st);
        b.append(DIV_CLOSE);
    }

    /**
     * Contexte : lieu et moment en une ligne italique sous le titre (comme le sous-titre
     * d'une rencontre dans un livre de JdR) ; repli en champs normaux si le texte est
     * long/multiligne.
     */
    private void sceneMeta(StringBuilder b, SceneJpaEntity sc) {
        if (isMetaShort(sc.getLocation()) && isMetaShort(sc.getTiming())
                && (notBlank(sc.getLocation()) || notBlank(sc.getTiming()))) {
            List<String> meta = new ArrayList<>();
            if (notBlank(sc.getLocation())) meta.add(sc.getLocation().trim());
            if (notBlank(sc.getTiming())) meta.add(sc.getTiming().trim());
            b.append("<div class=\"scene-meta\">").append(esc(String.join("  —  ", meta))).append(DIV_CLOSE);
        } else {
            block(b, "Lieu", sc.getLocation());
            block(b, "Moment", sc.getTiming());
        }
    }

    /**
     * Battlemaps (images uniquement ; les videos ne sont pas rendables en PDF). Le libellé
     * de la variante (Jour/Nuit…) est rendu en légende sous l'image.
     */
    private void sceneBattlemaps(StringBuilder b, SceneJpaEntity sc) {
        if (sc.getBattlemaps() == null) return;
        for (var bm : sc.getBattlemaps()) {
            PdfImage battlemap = imageEncoder.fileImage(bm.mediaFileId());
            if (battlemap == null) continue;
            String caption = bm.label() == null || bm.label().isBlank()
                    ? "Battlemap" : "Battlemap — " + bm.label();
            illustration(b, battlemap, caption);
        }
    }

    /** Pieces explorables de la scene (donjon, crypte...) — sous-blocs ambres. */
    private void rooms(StringBuilder b, SceneJpaEntity sc, Structure st) {
        if (sc.getRooms() == null || sc.getRooms().isEmpty()) return;
        List<Room> rooms = new ArrayList<>(sc.getRooms());
        rooms.sort(java.util.Comparator.comparingInt(Room::getOrder));
        Map<String, String> roomNames = new HashMap<>();
        for (Room r : rooms) roomNames.put(r.getId(), r.getName());

        for (Room r : rooms) {
            b.append("<div class=\"room\"><div class=\"room-head\"><span class=\"eyebrow\">Pièce");
            if (r.getFloor() != null) b.append(" · Étage ").append(r.getFloor());
            b.append(SPAN_CLOSE).append(esc(r.getName())).append(DIV_CLOSE);
            block(b, LABEL_DESCRIPTION, r.getDescription());
            box(b, "combat", "Ennemis", enemyLines(r.getEnemyIds(), r.getEnemies(), st));
            block(b, "Butin", r.getLoot());
            block(b, "Pièges", r.getTraps());
            box(b, CSS_SECRET, LABEL_NOTES_MJ, r.getGmNotes());
            PdfImage map = imageEncoder.image(r.getMapImageId(), PdfImageEncoder.ILLUSTRATION_MAX);
            if (map != null) illustration(b, map, "Plan — " + r.getName());
            illustrations(b, r.getIllustrationImageIds());
            if (r.getBranches() != null && !r.getBranches().isEmpty()) {
                List<String> parts = new ArrayList<>();
                for (RoomBranch br : r.getBranches()) {
                    parts.add(branchLine(roomNames.getOrDefault(br.targetRoomId(), "?"),
                            br.label(), null, br.condition()));
                }
                block(b, "Sorties", joinAsList(parts));
            }
            b.append(DIV_CLOSE);
        }
    }

    /** Sorties narratives d'une scene, cibles resolues en noms de scenes. */
    private String renderSceneBranches(List<SceneBranch> branches, Structure st) {
        if (branches == null || branches.isEmpty()) return null;
        List<String> parts = new ArrayList<>();
        for (SceneBranch br : branches) {
            String kind = switch (br.kind()) {
                case CLUE -> "indice";
                case LEAD -> "piste";
                case EXIT -> null;
            };
            parts.add(branchLine(st.sceneNames.getOrDefault(br.targetSceneId(), "?"),
                    br.label(), kind, br.condition()));
        }
        return joinAsList(parts);
    }

    /** "Vers <cible> — <libelle> [indice] (condition : ...)" avec les morceaux presents. */
    private static String branchLine(String target, String label, String kind, String condition) {
        StringBuilder line = new StringBuilder("Vers « ").append(target).append(" »");
        if (notBlank(label)) line.append(" — ").append(label.trim());
        if (kind != null) line.append(" [").append(kind).append("]");
        if (notBlank(condition)) line.append(" (condition : ").append(condition.trim()).append(")");
        return line.toString();
    }

    /**
     * Ennemis d'une rencontre : refs bestiaire resolues en noms (liste a puces)
     * + texte libre eventuel en paragraphe. Null si rien.
     */
    private String enemyLines(List<String> enemyIds, String freeText, Structure st) {
        List<String> names = new ArrayList<>();
        if (enemyIds != null) {
            for (String id : enemyIds) {
                String name = st.enemyNames.get(id);
                if (name != null) names.add(name);
            }
        }
        return joinNonBlank(joinAsList(names), freeText);
    }

    /** Joint les morceaux non vides par une ligne vide (nouveau paragraphe). Null si rien. */
    private static String joinNonBlank(String... parts) {
        StringBuilder out = new StringBuilder();
        for (String p : parts) {
            if (!notBlank(p)) continue;
            if (!out.isEmpty()) out.append("\n\n");
            out.append(p);
        }
        return out.isEmpty() ? null : out.toString();
    }

    // ----- Quetes libres / transversales (celles SANS conteneur dans la narration) -----

    private void quests(Ctx ctx, Structure st) {
        List<QuestJpaEntity> quests = st.standaloneQuests;
        if (quests.isEmpty()) return;

        StringBuilder b = ctx.body;
        b.append("<h1 class=\"part\" id=\"part-quests\">Quêtes</h1>");
        ctx.addToc(0, "Quêtes", "part-quests");
        StringBuilder qBms = new StringBuilder();
        for (QuestJpaEntity q : quests) qBms.append(bookmark(q.getName(), "quest-" + q.getId(), ""));
        ctx.bookmarks.append(bookmark("Quêtes", "part-quests", qBms.toString()));

        for (QuestJpaEntity q : quests) {
            ctx.addToc(1, q.getName(), "quest-" + q.getId());
            standaloneQuest(ctx, q, st);
        }
    }

    /** Une quete libre/transversale : ses champs + les scenes de ses conteneurs (arc SYSTEM masque). */
    private void standaloneQuest(Ctx ctx, QuestJpaEntity q, Structure st) {
        StringBuilder b = ctx.body;
        b.append("<div class=\"chapter\"><div class=\"chapter-head\" id=\"quest-").append(q.getId())
                .append("\"><span class=\"eyebrow\">Quête</span>").append(esc(q.getName())).append(DIV_CLOSE);
        illustrations(b, q.getIllustrationImageIds());
        block(b, LABEL_DESCRIPTION, q.getDescription());
        block(b, "Conditions de déblocage", renderPrerequisites(q.getPrerequisites(), st.questNames));
        block(b, "Nœuds liés", renderQuestNodes(q, st));
        block(b, LABEL_OBJECTIFS_JOUEURS, q.getPlayerObjectives());
        block(b, LABEL_ENJEUX_NARRATIFS, q.getNarrativeStakes());
        box(b, CSS_SECRET, LABEL_NOTES_MJ, q.getGmNotes());
        // Scenes de ses conteneurs (arc SYSTEM, masque de la narration) : c'est ICI
        // que vit le contenu jouable d'une quete libre.
        questContainerScenes(ctx, q, st);
        b.append(DIV_CLOSE);
    }

    private void questContainerScenes(Ctx ctx, QuestJpaEntity q, Structure st) {
        if (q.getNodes() == null) return;
        Set<String> containers = st.containerChapterIds(q);
        for (QuestNodeRef n : q.getNodes()) {
            for (SceneJpaEntity sc : containerScenes(n, containers, st)) {
                scene(ctx.body, sc, st);
                ctx.scenes++;
            }
        }
    }

    /** Scenes du chapitre reference par ce noeud, si c'est bien un conteneur de la quete. */
    private static List<SceneJpaEntity> containerScenes(QuestNodeRef n, Set<String> containers, Structure st) {
        if (n.nodeType() != NodeType.CHAPTER || !containers.contains(n.nodeId())) return List.of();
        List<SceneJpaEntity> scenes = st.scenesByChapter.get(Long.parseLong(n.nodeId()));
        return scenes != null ? scenes : List.of();
    }

    /** Prérequis d'une quête en texte lisible (une ligne par condition). */
    private String renderPrerequisites(List<Prerequisite> prereqs, Map<String, String> questNames) {
        if (prereqs == null || prereqs.isEmpty()) return null;
        List<String> parts = new ArrayList<>();
        for (Prerequisite p : prereqs) {
            if (p instanceof Prerequisite.QuestCompleted qc) {
                parts.add("Quête « " + questNames.getOrDefault(qc.questId(), "?") + " » terminée");
            } else if (p instanceof Prerequisite.SessionReached sr) {
                parts.add("Séance " + sr.minSessionNumber() + " atteinte");
            } else if (p instanceof Prerequisite.FlagSet fs) {
                parts.add("Fait : " + fs.flagName());
            }
        }
        return joinAsList(parts);
    }

    /**
     * Nœuds narratifs (chapitres / scènes) traversés par la quête, en texte lisible.
     * Les chapitres-CONTENEURS de la quete sont exclus : c'est de la plomberie (le jumeau
     * porte le meme nom que la quete), pas un lien narratif.
     */
    private String renderQuestNodes(QuestJpaEntity q, Structure st) {
        if (q.getNodes() == null || q.getNodes().isEmpty()) return null;
        Set<String> containers = st.containerChapterIds(q);
        List<String> parts = new ArrayList<>();
        for (QuestNodeRef n : q.getNodes()) {
            if (n.nodeType() == NodeType.SCENE) {
                parts.add("Scène : " + st.sceneNames.getOrDefault(n.nodeId(), "?"));
            } else if (!containers.contains(n.nodeId())) {
                parts.add("Chapitre : " + st.chapterNames.getOrDefault(n.nodeId(), "?"));
            }
        }
        return joinAsList(parts);
    }

    /** Plusieurs elements -> lignes "- ..." (rendues en vraies puces par parseBlocks). */
    private static String joinAsList(List<String> parts) {
        if (parts.isEmpty()) return null;
        if (parts.size() == 1) return parts.get(0);
        StringBuilder s = new StringBuilder();
        for (String p : parts) {
            if (!s.isEmpty()) s.append('\n');
            s.append("- ").append(p);
        }
        return s.toString();
    }

    // ----- PNJ / Ennemis (groupes par dossier) -----

    /** Une fiche persona generique pour la mise en page (PNJ ou ennemi). */
    private record PersonaRow(int order, String name, String folder, String level, String portraitId,
                              Map<String, String> values, Map<String, Map<String, String>> keyValueValues,
                              Map<String, List<String>> imageValues, Map<String, String> foundryStats) {}

    private List<PersonaRow> npcEntries(CampaignJpaEntity c) {
        List<PersonaRow> out = new ArrayList<>();
        for (NpcJpaEntity n : npcRepo.findByCampaignIdOrderByOrderAsc(c.getId())) {
            out.add(new PersonaRow(n.getOrder(), n.getName(), n.getFolder(), null, n.getPortraitImageId(),
                    n.getValues(), n.getKeyValueValues(), n.getImageValues(), null));
        }
        return out;
    }

    private List<PersonaRow> enemyEntries(CampaignJpaEntity c) {
        List<PersonaRow> out = new ArrayList<>();
        for (EnemyJpaEntity e : enemyRepo.findByCampaignIdOrderByOrderAsc(c.getId())) {
            out.add(new PersonaRow(e.getOrder(), e.getName(), e.getFolder(), e.getLevel(), e.getPortraitImageId(),
                    e.getValues(), e.getKeyValueValues(), e.getImageValues(), e.getFoundryStats()));
        }
        return out;
    }

    private void personas(Ctx ctx, String partId, String title,
                          List<PersonaRow> rows, List<TemplateField> template, boolean enemy) {
        if (rows.isEmpty()) return;
        if (enemy) ctx.enemies = rows.size();
        else ctx.npcs = rows.size();

        StringBuilder b = ctx.body;
        b.append("<h1 class=\"part\" id=\"").append(partId).append("\">").append(esc(title)).append("</h1>");
        ctx.addToc(0, title, partId);

        FolderGroups groups = groupByFolder(rows);
        StringBuilder fBms = new StringBuilder();
        int fi = 0;
        for (Map.Entry<String, List<PersonaRow>> e : groups.byFolder().entrySet()) {
            String anchor = partId + "-f" + (fi++);
            personaFolderSection(ctx, template, enemy, anchor, e.getKey(), e.getValue(), fBms);
        }
        if (!groups.ungrouped().isEmpty()) {
            String ungroupedAnchor = groups.byFolder().isEmpty() ? null : partId + "-f" + fi;
            personaUngroupedSection(ctx, template, enemy, ungroupedAnchor, groups.ungrouped(), fBms);
        }
        ctx.bookmarks.append(bookmark(title, partId, fBms.toString()));
    }

    /** Regroupement par dossier (dossiers tries, non-classes a part). */
    private record FolderGroups(Map<String, List<PersonaRow>> byFolder, List<PersonaRow> ungrouped) {}

    private static FolderGroups groupByFolder(List<PersonaRow> rows) {
        Map<String, List<PersonaRow>> byFolder = new TreeMap<>();
        List<PersonaRow> ungrouped = new ArrayList<>();
        for (PersonaRow r : rows) {
            String f = r.folder() != null ? r.folder().trim() : "";
            if (f.isEmpty()) ungrouped.add(r);
            else byFolder.computeIfAbsent(f, k -> new ArrayList<>()).add(r);
        }
        return new FolderGroups(byFolder, ungrouped);
    }

    private void personaFolderSection(Ctx ctx, List<TemplateField> template, boolean enemy, String anchor,
                                      String folderKey, List<PersonaRow> rows, StringBuilder fBms) {
        StringBuilder b = ctx.body;
        String label = folderKey.replace("/", " / ");
        ctx.addToc(1, label, anchor);
        fBms.append(bookmark(label, anchor, ""));
        b.append(H3_FOLDER_OPEN).append(anchor).append("\">").append(esc(label)).append("</h3>");
        rows.sort(java.util.Comparator.comparingInt(PersonaRow::order));
        for (PersonaRow r : rows) personaCard(b, r, template, enemy);
    }

    private void personaUngroupedSection(Ctx ctx, List<TemplateField> template, boolean enemy, String anchor,
                                         List<PersonaRow> ungrouped, StringBuilder fBms) {
        StringBuilder b = ctx.body;
        if (anchor != null) {
            ctx.addToc(1, LABEL_SANS_DOSSIER, anchor);
            fBms.append(bookmark(LABEL_SANS_DOSSIER, anchor, ""));
            b.append(H3_FOLDER_OPEN).append(anchor).append("\">Sans dossier</h3>");
        }
        ungrouped.sort(java.util.Comparator.comparingInt(PersonaRow::order));
        for (PersonaRow r : ungrouped) personaCard(b, r, template, enemy);
    }

    private void personaCard(StringBuilder b, PersonaRow r, List<TemplateField> template, boolean enemy) {
        // Mise en page en TABLE (portrait | contenu) : openhtmltopdf gere mal le float
        // + overflow (le texte se superposait au portrait).
        b.append("<div class=\"card\"><table class=\"persona\"><tr>");
        PdfImage portrait = imageEncoder.image(r.portraitId(), PdfImageEncoder.PORTRAIT_MAX);
        if (portrait != null) {
            double widthCm = Math.min(3.0, PORTRAIT_MAX_HEIGHT_CM * portrait.w() / portrait.h());
            b.append("<td class=\"persona-portrait\"><img style=\"width:").append(cm(widthCm))
                    .append("\" src=\"").append(portrait.uri()).append("\"/></td>");
        }
        b.append("<td class=\"persona-content\">");
        b.append("<div class=\"persona-name\">").append(esc(r.name()));
        if (notBlank(r.level())) b.append(" <span class=\"level\">Niv. ").append(esc(r.level())).append(SPAN_CLOSE);
        b.append(DIV_CLOSE);

        renderFields(b, template, r.values(), r.keyValueValues(), r.imageValues(), null);

        if (enemy && r.foundryStats() != null) {
            Map<String, String> clean = cleanStats(r.foundryStats());
            if (!clean.isEmpty()) {
                b.append("<div class=\"field-label\">Statistiques</div><table class=\"stats-table\"><tbody>");
                int row = 0;
                for (Map.Entry<String, String> s : clean.entrySet()) {
                    b.append(trTag(row++))
                            .append("<th>").append(esc(s.getKey())).append("</th><td>")
                            .append(esc(s.getValue())).append(TD_TR_CLOSE);
                }
                b.append(TBODY_TABLE_CLOSE);
            }
        }
        b.append("</td></tr></table></div>");
    }

    /** Valeurs de stats considerees comme "vides"/bruit (masquees dans le livret). */
    private static final Set<String> STAT_NOISE_VALUES = Set.of("0", "0.0", "false", "none", "null", "", "-", "—");
    /** Premier segment de chemin a elaguer du libelle (purement structurel). */
    private static final Set<String> STAT_DROP_PREFIX = Set.of("attributes", "details", "system", "data");

    /**
     * Nettoie le snapshot de stats Foundry pour le livret : retire le bruit (valeurs
     * 0/false/none/vides, options techniques type rollMode) et humanise les clés
     * ("attributes.hp.value" -> "Hp value"). Conserve l'ordre alphabetique des clés.
     */
    private static Map<String, String> cleanStats(Map<String, String> stats) {
        Map<String, String> out = new LinkedHashMap<>();
        for (Map.Entry<String, String> e : new TreeMap<>(stats).entrySet()) {
            String v = cleanStatValue(e.getKey(), e.getValue());
            if (v != null) out.put(humanizeStatKey(e.getKey()), v);
        }
        return out;
    }

    /** Valeur nettoyee d'une stat, ou null si bruit (0/false/none/vide, ou option rollMode technique). */
    private static String cleanStatValue(String key, String val) {
        if (key == null || val == null) return null;
        String v = val.trim();
        if (STAT_NOISE_VALUES.contains(v.toLowerCase())) return null;
        String lk = key.toLowerCase();
        if (lk.contains("rollmode") || lk.endsWith(".defaultrollmode")) return null;
        return v;
    }

    /** "attributes.hp.value" -> "Hp value" ; "details.creatureType" -> "Creature type". */
    private static String humanizeStatKey(String key) {
        String[] parts = key.split("\\.");
        int start = (parts.length > 1 && STAT_DROP_PREFIX.contains(parts[0].toLowerCase())) ? 1 : 0;
        String s = String.join(" ", Arrays.asList(parts).subList(start, parts.length));
        s = s.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase().trim(); // camelCase -> mots
        return s.isEmpty() ? key : Character.toUpperCase(s.charAt(0)) + s.substring(1);
    }

    // ----- Tables aleatoires (le contenu le plus « papier » du livret) -----

    private void randomTables(Ctx ctx, CampaignJpaEntity campaign) {
        List<RandomTableJpaEntity> tables = randomTableRepo.findByCampaignIdOrderByOrderAsc(campaign.getId());
        if (tables.isEmpty()) return;
        ctx.randomTables = tables.size();

        StringBuilder b = ctx.body;
        b.append("<h1 class=\"part\" id=\"part-tables\">Tables aléatoires</h1>");
        ctx.addToc(0, "Tables aléatoires", "part-tables");
        StringBuilder tBms = new StringBuilder();
        for (RandomTableJpaEntity t : tables) tBms.append(bookmark(t.getName(), "rt-" + t.getId(), ""));
        ctx.bookmarks.append(bookmark("Tables aléatoires", "part-tables", tBms.toString()));

        for (RandomTableJpaEntity t : tables) {
            ctx.addToc(1, t.getName(), "rt-" + t.getId());
            randomTableCard(b, t);
        }
    }

    private void randomTableCard(StringBuilder b, RandomTableJpaEntity t) {
        b.append("<div class=\"card\" id=\"rt-").append(t.getId()).append("\"><div class=\"card-body\">");
        b.append("<div class=\"persona-name\">").append(esc(t.getName()));
        if (notBlank(t.getDiceFormula())) {
            b.append(" <span class=\"level\">").append(esc(t.getDiceFormula())).append(SPAN_CLOSE);
        }
        b.append(DIV_CLOSE);
        block(b, LABEL_DESCRIPTION, t.getDescription());
        if (!t.getEntries().isEmpty()) {
            randomTableEntries(b, t);
        }
        b.append("</div></div>");
    }

    private void randomTableEntries(StringBuilder b, RandomTableJpaEntity t) {
        b.append("<table class=\"stats-table\"><thead><tr><th class=\"roll-col\">")
                .append(esc(notBlank(t.getDiceFormula()) ? t.getDiceFormula() : "Jet"))
                .append("</th><th>Résultat</th></tr></thead><tbody>");
        int row = 0;
        for (RandomTableEntryJpaEntity e : t.getEntries()) {
            String roll = e.getMinRoll() == e.getMaxRoll()
                    ? String.valueOf(e.getMinRoll())
                    : e.getMinRoll() + "–" + e.getMaxRoll();
            b.append(trTag(row++))
                    .append("<td class=\"roll-col\">").append(esc(roll)).append("</td><td>")
                    .append(esc(e.getLabel()));
            if (notBlank(e.getDetail())) {
                b.append("<br/><span class=\"entry-detail\">").append(multiline(e.getDetail())).append(SPAN_CLOSE);
            }
            b.append(TD_TR_CLOSE);
        }
        b.append(TBODY_TABLE_CLOSE);
    }

    // ----- Lore (pages groupees par dossier) -----

    private void lore(Ctx ctx, CampaignJpaEntity campaign) {
        String loreId = campaign.getLoreId();
        if (loreId == null || loreId.isBlank()) return;
        Long lid;
        try { lid = Long.parseLong(loreId); } catch (NumberFormatException ex) { return; }

        List<PageJpaEntity> pages = pageRepo.findByLoreId(lid);
        if (pages.isEmpty()) return;
        ctx.lorePages = pages.size();

        StringBuilder b = ctx.body;
        b.append("<h1 class=\"part\" id=\"part-lore\">Lore</h1>");
        ctx.addToc(0, "Lore", "part-lore");

        // Chemins de dossiers (LoreNode) + templates par id.
        Map<Long, LoreNodeJpaEntity> nodes = new HashMap<>();
        for (LoreNodeJpaEntity n : loreNodeRepo.findByLoreId(lid)) nodes.put(n.getId(), n);
        Map<Long, TemplateJpaEntity> templates = new HashMap<>();
        for (TemplateJpaEntity t : templateRepo.findByLoreId(lid)) templates.put(t.getId(), t);

        Map<String, List<PageJpaEntity>> byPath = new TreeMap<>();
        for (PageJpaEntity p : pages) {
            byPath.computeIfAbsent(nodePath(p.getNodeId(), nodes), k -> new ArrayList<>()).add(p);
        }
        StringBuilder fBms = new StringBuilder();
        int fi = 0;
        for (Map.Entry<String, List<PageJpaEntity>> e : byPath.entrySet()) {
            String label = e.getKey().isEmpty() ? LABEL_SANS_DOSSIER : e.getKey();
            String anchor = "lore-f" + (fi++);
            ctx.addToc(1, label, anchor);
            fBms.append(bookmark(label, anchor, ""));
            b.append(H3_FOLDER_OPEN).append(anchor).append("\">").append(esc(label)).append("</h3>");
            e.getValue().sort(java.util.Comparator.comparingInt(PageJpaEntity::getOrder));
            for (PageJpaEntity p : e.getValue()) {
                b.append("<div class=\"card\"><div class=\"card-body\"><div class=\"persona-name\">")
                        .append(esc(p.getTitle())).append(DIV_CLOSE);
                List<TemplateField> tpl = p.getTemplateId() != null
                        ? fieldsOf(templates.get(p.getTemplateId())) : null;
                renderFields(b, tpl, p.getValues(), p.getKeyValueValues(), p.getImageValues(), p.getTableValues());
                b.append("</div></div>");
            }
        }
        ctx.bookmarks.append(bookmark("Lore", "part-lore", fBms.toString()));
    }

    private String nodePath(Long nodeId, Map<Long, LoreNodeJpaEntity> nodes) {
        if (nodeId == null) return "";
        Deque<String> parts = new ArrayDeque<>();
        Long cur = nodeId;
        int guard = 0;
        while (cur != null && guard++ < 30) {
            LoreNodeJpaEntity n = nodes.get(cur);
            if (n == null) break;
            if (n.getName() != null) parts.addFirst(n.getName());
            cur = n.getParentId();
        }
        return String.join(" / ", parts);
    }

    // ----- Rendu des champs pilotes par template -----

    private void renderFields(StringBuilder b, List<TemplateField> template, Map<String, String> values,
                              Map<String, Map<String, String>> keyValueValues,
                              Map<String, List<String>> imageValues,
                              Map<String, List<Map<String, String>>> tableValues) {
        // Repli : pas de template -> paires brutes cle=valeur.
        if (template == null || template.isEmpty()) {
            if (values != null) {
                for (Map.Entry<String, String> e : values.entrySet()) block(b, e.getKey(), e.getValue());
            }
            return;
        }
        for (TemplateField f : template) {
            if (f == null || f.getName() == null || f.getType() == null) continue;
            renderField(b, f, values, keyValueValues, imageValues, tableValues);
        }
    }

    private void renderField(StringBuilder b, TemplateField f, Map<String, String> values,
                              Map<String, Map<String, String>> keyValueValues,
                              Map<String, List<String>> imageValues,
                              Map<String, List<Map<String, String>>> tableValues) {
        switch (f.getType()) {
            case TEXT, NUMBER -> block(b, f.getName(), values != null ? values.get(f.getName()) : null);
            case KEY_VALUE_LIST -> renderKeyValueField(b, f, keyValueValues);
            case IMAGE -> illustrations(b, imageValues != null ? imageValues.get(f.getName()) : null);
            case TABLE -> renderTableField(b, f, tableValues);
        }
    }

    private void renderKeyValueField(StringBuilder b, TemplateField f, Map<String, Map<String, String>> keyValueValues) {
        Map<String, String> inner = keyValueValues != null ? keyValueValues.get(f.getName()) : null;
        List<String> labels = f.getLabels();
        if (inner != null && labels != null && labels.stream().anyMatch(l -> notBlank(inner.get(l)))) {
            keyValueTable(b, f.getName(), labels, inner);
        }
    }

    private void renderTableField(StringBuilder b, TemplateField f, Map<String, List<Map<String, String>>> tableValues) {
        List<Map<String, String>> data = tableValues != null ? tableValues.get(f.getName()) : null;
        List<String> cols = f.getLabels();
        if (data == null || data.isEmpty() || cols == null || cols.isEmpty()) return;
        b.append("<div class=\"field-label\">").append(esc(f.getName()))
                .append("</div><table class=\"stats-table\"><thead><tr>");
        for (String col : cols) b.append("<th>").append(esc(col)).append("</th>");
        b.append("</tr></thead><tbody>");
        int row = 0;
        for (Map<String, String> line : data) {
            b.append(trTag(row++));
            for (String col : cols) b.append("<td>").append(esc(line.get(col))).append("</td>");
            b.append("</tr>");
        }
        b.append(TBODY_TABLE_CLOSE);
    }

    /**
     * Liste cle/valeur : en TABLEAU HORIZONTAL compact quand cela ressemble a une ligne de
     * caracteristiques (peu d'entrees, libelles et valeurs courts — FOR/DEX/CON...),
     * sinon en tableau vertical classique a deux colonnes.
     */
    private void keyValueTable(StringBuilder b, String name, List<String> labels, Map<String, String> inner) {
        b.append("<div class=\"field-label\">").append(esc(name)).append(DIV_CLOSE);
        if (isCompactKeyValue(labels, inner)) {
            keyValueTableCompact(b, labels, inner);
        } else {
            keyValueTableVertical(b, labels, inner);
        }
    }

    private static boolean isCompactKeyValue(List<String> labels, Map<String, String> inner) {
        return labels.size() >= 2 && labels.size() <= 8
                && labels.stream().allMatch(l -> l != null && l.length() <= 5)
                && labels.stream().allMatch(l -> {
                    String v = inner.get(l);
                    return v == null || v.trim().length() <= 8;
                });
    }

    private void keyValueTableCompact(StringBuilder b, List<String> labels, Map<String, String> inner) {
        b.append("<table class=\"stat-array\"><tr>");
        for (String label : labels) b.append("<th>").append(esc(label)).append("</th>");
        b.append("</tr><tr>");
        for (String label : labels) {
            String v = inner.get(label);
            b.append("<td>").append(notBlank(v) ? esc(v) : "—").append("</td>");
        }
        b.append("</tr></table>");
    }

    private void keyValueTableVertical(StringBuilder b, List<String> labels, Map<String, String> inner) {
        b.append("<table class=\"stats-table\"><tbody>");
        int row = 0;
        for (String label : labels) {
            String v = inner.get(label);
            if (!notBlank(v)) continue;
            b.append(trTag(row++))
                    .append("<th>").append(esc(label)).append("</th><td>")
                    .append(esc(v)).append(TD_TR_CLOSE);
        }
        b.append(TBODY_TABLE_CLOSE);
    }

    // ----- Illustrations -----

    /** Galerie d'illustrations (liste d'ids d'images) -> blocs image centres, hauteur bornee. */
    private void illustrations(StringBuilder b, List<String> imageIds) {
        if (imageIds == null) return;
        for (String id : imageIds) {
            PdfImage img = imageEncoder.image(id, PdfImageEncoder.ILLUSTRATION_MAX);
            if (img != null) illustration(b, img, null);
        }
    }

    /**
     * Une illustration centree, avec legende optionnelle. La largeur est calculee pour que
     * la hauteur affichee reste bornee : une image tres verticale ne mange plus la page.
     */
    private void illustration(StringBuilder b, PdfImage img, String caption) {
        double widthCm = Math.min(CONTENT_WIDTH_CM,
                ILLUSTRATION_MAX_HEIGHT_CM * img.w() / img.h());
        b.append("<div class=\"illus\"><img style=\"width:").append(cm(widthCm))
                .append("\" src=\"").append(img.uri()).append("\"/>");
        if (caption != null) {
            b.append("<div class=\"caption\">").append(esc(caption)).append(DIV_CLOSE);
        }
        b.append(DIV_CLOSE);
    }

    // ====================================================================== Helpers

    private List<TemplateField> resolveTemplate(String gameSystemId, boolean npc) {
        if (gameSystemId == null || gameSystemId.isBlank()) return List.of();
        try {
            List<TemplateField> fields = gameSystemRepo.findById(Long.parseLong(gameSystemId))
                    .map(gs -> npc ? gs.getNpcTemplate() : gs.getEnemyTemplate())
                    .orElse(null);
            return fields != null ? fields : List.of();
        } catch (NumberFormatException ex) {
            return List.of();
        }
    }

    private static List<TemplateField> fieldsOf(TemplateJpaEntity t) {
        return t != null ? t.getFields() : null;
    }

    /** CSS final : en-tete courant renseigne avec le nom de la campagne. */
    // S125 : faux positif — commentaire explicatif (prose), pas de code mort.
    @SuppressWarnings("java:S125")
    private static String cssFor(String campaignName) {
        String header = campaignName == null ? "" : campaignName.trim();
        if (header.length() > 70) header = header.substring(0, 69) + "…";
        // Echappement CSS (chaine entre quotes) PUIS XML : le CSS vit dans <style>,
        // du PCDATA — un '&' ou '<' brut casserait le parse du document entier.
        return CSS.replace("__HEADER__", esc(header.replace("\\", "\\\\").replace("'", "\\'")));
    }

    // CSS print (CSS 2.1 + paged media supporte par openhtmltopdf : pas de flexbox/grid).
    // Polices de base PDF : serif (Times) pour le texte courant — plus lisible en long —,
    // sans-serif (Helvetica) pour titres, libelles et tables.
    // ATTENTION : ce CSS vit dans <style> (PCDATA XML) — jamais de '&' ici, meme en commentaire.
    // Contenu dans resources/pdf/export.css (extrait pour alleger cette classe).
    // S125 : faux positif — le bloc ci-dessus est de la prose explicative, pas du code mort.
    @SuppressWarnings("java:S125")
    private static final String CSS = loadCss();

    private static String loadCss() {
        try (InputStream in = PdfExportService.class.getResourceAsStream("/pdf/export.css")) {
            if (in == null) {
                throw new IllegalStateException("Ressource pdf/export.css introuvable dans le classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de charger le CSS d'export PDF", e);
        }
    }
}
