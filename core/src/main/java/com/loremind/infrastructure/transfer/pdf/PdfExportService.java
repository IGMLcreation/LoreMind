package com.loremind.infrastructure.transfer.pdf;

import com.loremind.domain.campaigncontext.CampaignPublication;
import com.loremind.domain.campaigncontext.bestiary.NpcStory;
import com.loremind.domain.campaigncontext.quest.NodeType;
import com.loremind.domain.campaigncontext.quest.Prerequisite;
import com.loremind.domain.campaigncontext.quest.QuestNodeRef;
import com.loremind.domain.campaigncontext.structure.Room;
import com.loremind.domain.campaigncontext.structure.RoomBranch;
import com.loremind.domain.campaigncontext.structure.SceneBranch;
import com.loremind.domain.shared.template.TemplateField;
import com.loremind.infrastructure.persistence.entity.*;
import com.loremind.infrastructure.persistence.jpa.*;
import com.loremind.infrastructure.transfer.pdf.PdfExportOptions.LoreScope;
import com.loremind.infrastructure.transfer.pdf.PdfExportOptions.Section;
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

    /** Hauteur max affichee d'une illustration : evite qu'une image portrait mange la page. */
    private static final double ILLUSTRATION_MAX_HEIGHT_CM = 11.0;
    /** Hauteur max affichee d'un portrait de fiche (colonne de 3cm de large). */
    private static final double PORTRAIT_MAX_HEIGHT_CM = 4.5;

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
    private static final String LABEL_THEMES = "Thèmes";
    private static final String LABEL_ENJEUX = "Enjeux";
    private static final String LABEL_RECOMPENSES = "Récompenses";
    private static final String LABEL_RESOLUTION = "Résolution";
    private static final String LABEL_CONDITIONS = "Conditions de déblocage";
    private static final String LABEL_NOEUDS = "Nœuds liés";

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

    /** Livret MJ complet en A4 (options par défaut). @throws NoSuchElementException si absente. */
    public byte[] export(String campaignId) {
        return export(campaignId, PdfExportOptions.defaults());
    }

    /** Construit le PDF de la campagne selon les options. @throws NoSuchElementException si absente. */
    public byte[] export(String campaignId, PdfExportOptions options) {
        options = options != null ? options : PdfExportOptions.defaults();
        String xhtml = xhtml(campaignId, options);
        try {
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            PdfRendererBuilder builder = new PdfRendererBuilder();
            builder.useFastMode();
            registerFonts(builder);
            builder.withHtmlContent(xhtml, null);
            builder.toStream(out);
            try (var renderer = builder.buildPdfRenderer(); var document = renderer.getPdfDocument()) {
                renderer.layout();
                renderer.createPDFWithoutClosing();
                PdfPageOrnaments.draw(document, options);
                document.save(out);
            }
            return out.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Echec de la generation du PDF", e);
        }
    }

    /** Document XHTML intermédiaire (exposé au paquet pour les tests de contenu). */
    String xhtml(String campaignId, PdfExportOptions options) {
        return buildXhtml(campaign(campaignId), options != null ? options : PdfExportOptions.defaults());
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
    static final class Ctx {
        final PdfExportOptions opt;
        /** Version MJ : les contenus reserves au MJ (secrets, consequences, combats...) sont rendus. */
        final boolean gm;
        final StringBuilder body = new StringBuilder();
        /** Cartes rendues en annexe (par scene), ou null si l'annexe Cartes n'est pas demandee. */
        Map<Long, List<PdfAppendices.MapPage>> maps;
        final StringBuilder bookmarks = new StringBuilder();
        final List<TocEntry> toc = new ArrayList<>();
        int scenes;
        int quests;
        int npcs;
        int enemies;
        int randomTables;
        int lorePages;
        private boolean columns;

        Ctx(PdfExportOptions opt) {
            this.opt = opt;
            this.gm = opt.gmContent();
        }

        void addToc(int level, String title, String anchor) {
            toc.add(new TocEntry(level, title, anchor));
        }

        double contentWidthCm() {
            double width = opt.format().contentWidthCm();
            return columns ? (width - 0.8) / 2 : width;
        }

        /** Le petit format garde une colonne ; les tables et cartes restent hors du flux. */
        void beginColumns() {
            columns = opt.format() == PdfExportOptions.PageFormat.A4;
            if (columns) body.append("<div class=\"editorial-flow\">");
        }

        void endColumns() {
            if (columns) body.append(DIV_CLOSE);
            columns = false;
        }

        /** Champ reserve au MJ : absent de la version joueurs. */
        void gmBlock(String label, String value) {
            if (gm) block(body, label, value);
        }

        /** Encadre reserve au MJ : absent de la version joueurs. */
        void gmBox(String cssClass, String label, String value) {
            if (gm) box(body, cssClass, label, value);
        }
    }

    private String buildXhtml(CampaignJpaEntity campaign, PdfExportOptions opt) {
        List<TemplateField> npcTemplate = resolveTemplate(campaign.getGameSystemId(), true);
        List<TemplateField> enemyTemplate = resolveTemplate(campaign.getGameSystemId(), false);
        CampaignPublication pub = campaign.getPublication() != null
                ? campaign.getPublication() : CampaignPublication.empty();

        // Les sections d'abord (elles remplissent sommaire + compteurs), la couverture
        // et le sommaire sont assembles ensuite en tete de document.
        Structure st = structureLoader.load(campaign);
        Ctx ctx = new Ctx(opt);
        // Cartes encodees d'abord : les scenes y renvoient au lieu de les afficher en petit.
        if (opt.includes(Section.MAPS)) ctx.maps = PdfAppendices.collectMaps(st, imageEncoder);
        List<PersonaRow> npcRows = opt.includes(Section.NPCS) ? npcEntries(campaign) : List.of();
        List<PersonaRow> enemyRows = opt.includes(Section.ENEMIES) ? enemyEntries(campaign) : List.of();
        introduction(ctx, campaign, pub, st);
        if (opt.includes(Section.NARRATIVE)) narrative(ctx, st);
        if (opt.includes(Section.QUESTS)) {
            ctx.quests = st.quests.size();
            quests(ctx, st);
        }
        personas(ctx, "part-npcs", "Personnages non-joueurs (PNJ)", npcRows, npcTemplate, false);
        personas(ctx, "part-enemies", "Bestiaire", enemyRows, enemyTemplate, true);
        if (opt.includes(Section.TABLES)) randomTables(ctx, campaign);
        if (opt.includes(Section.LORE)) lore(ctx, campaign, st);
        if (opt.includes(Section.APPENDIX) && ctx.gm) appendix(ctx, st, npcRows, enemyRows);
        if (ctx.maps != null) PdfAppendices.mapsPart(ctx, ctx.maps);

        StringBuilder full = new StringBuilder();
        cover(full, ctx, campaign, pub);
        creditsPage(full, campaign, pub);
        full.append(tocHtml(ctx.toc));
        full.append(ctx.body);
        String body = resolveRefs(full.toString());

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\" lang=\"fr\" xml:lang=\"fr\"><head><meta charset=\"utf-8\"/>\n"
                + documentInfo(campaign, pub)
                + "<style>" + cssFor(campaign.getName(), opt) + "</style>\n"
                + "<bookmarks>" + ctx.bookmarks + "</bookmarks>\n"
                + "</head><body>" + body + "</body></html>";
    }

    /**
     * Annexes MJ : revelations et indices, index des PNJ et des adversaires (avec leurs
     * apparitions). Les index ne listent que les fiches presentes dans le livret.
     */
    private void appendix(Ctx ctx, Structure st, List<PersonaRow> npcRows, List<PersonaRow> enemyRows) {
        StringBuilder annex = new StringBuilder();
        PdfAppendices.revelations(annex, st);
        PdfAppendices.index(annex, "annex-npcs", "Index des PNJ", indexRows(npcRows, "npc-"), null);
        PdfAppendices.index(annex, "annex-enemies", "Index des adversaires", indexRows(enemyRows, "enemy-"),
                PdfAppendices.enemyAppearances(st));
        if (annex.isEmpty()) return;
        ctx.body.append("<h1 class=\"part\" id=\"part-annex\">Annexes</h1>").append(annex);
        ctx.addToc(0, "Annexes", "part-annex");
        ctx.bookmarks.append(bookmark("Annexes", "part-annex", ""));
    }

    private static List<PdfAppendices.IndexRow> indexRows(List<PersonaRow> rows, String anchorPrefix) {
        List<PdfAppendices.IndexRow> out = new ArrayList<>();
        for (PersonaRow r : rows) {
            out.add(new PdfAppendices.IndexRow(anchorPrefix + r.id(), r.name(), r.folder(), r.level()));
        }
        return out;
    }

    /**
     * Proprietes du fichier PDF (titre, auteur, sujet, mots-cles) lues par les liseuses
     * et les boutiques : openhtmltopdf les reprend du {@code <title>} et des {@code <meta>}.
     */
    private static String documentInfo(CampaignJpaEntity c, CampaignPublication pub) {
        StringBuilder m = new StringBuilder();
        m.append("<title>").append(esc(c.getName())).append("</title>\n");
        meta(m, "author", pub.author());
        meta(m, "subject", notBlank(pub.subtitle()) ? pub.subtitle() : firstLine(pub.pitch()));
        meta(m, "keywords", pub.system());
        return m.toString();
    }

    private static void meta(StringBuilder m, String name, String content) {
        if (!notBlank(content)) return;
        m.append("<meta name=\"").append(name).append("\" content=\"").append(esc(content.trim())).append("\"/>\n");
    }

    private static String firstLine(String s) {
        if (!notBlank(s)) return null;
        String line = s.strip().split("\\R", 2)[0];
        return line.length() > 200 ? line.substring(0, 199) + "…" : line;
    }

    /**
     * Couverture. Sans fiche Publication : le livret historique (titre, compteurs, description).
     * Avec : systeme en surtitre, sous-titre, illustration, ligne technique, auteur, version —
     * jamais de date d'export (un module vendu n'a pas a dater sa generation).
     */
    private void cover(StringBuilder b, Ctx ctx, CampaignJpaEntity c, CampaignPublication pub) {
        boolean published = pub.hasContent();
        PdfImage art = imageEncoder.image(pub.coverImageId(), PdfImageEncoder.ILLUSTRATION_MAX);
        if (art != null && ctx.opt.format() == PdfExportOptions.PageFormat.A4 && !ctx.opt.printFriendly()) {
            illustratedCover(b, ctx, c, pub, art);
            return;
        }
        b.append("<div class=\"cover").append(art != null ? " has-art" : "").append("\">");
        String kicker = coverKicker(ctx, pub);
        if (kicker != null) b.append("<div class=\"cover-kicker\">").append(esc(kicker)).append(DIV_CLOSE);
        b.append("<h1 class=\"cover-title\">").append(esc(c.getName())).append("</h1>");
        if (notBlank(pub.subtitle())) {
            b.append("<div class=\"cover-subtitle\">").append(esc(pub.subtitle())).append(DIV_CLOSE);
        }
        b.append("<div class=\"cover-rule\"></div>");
        if (art != null) {
            double maxH = ctx.opt.format().coverArtMaxCm;
            double widthCm = Math.min(ctx.contentWidthCm(), maxH * art.w() / art.h());
            b.append("<div class=\"cover-art\"><img style=\"width:").append(cm(widthCm))
                    .append("\" src=\"").append(art.uri()).append("\"/></div>");
        }
        String meta = published ? technicalLine(pub) : coverMeta(ctx);
        if (!meta.isEmpty()) {
            b.append("<div class=\"cover-meta\">").append(esc(meta)).append(DIV_CLOSE);
        }
        if (!ctx.gm && published) {
            b.append("<div class=\"cover-edition\">Édition joueurs</div>");
        }
        if (!published && notBlank(c.getDescription())) {
            b.append("<div class=\"cover-desc\">").append(multiline(c.getDescription())).append(DIV_CLOSE);
        }
        if (notBlank(pub.author())) {
            b.append("<div class=\"cover-author\">").append(esc(pub.author())).append(DIV_CLOSE);
        }
        if (notBlank(pub.version())) {
            b.append("<div class=\"cover-version\">Version ").append(esc(pub.version())).append(DIV_CLOSE);
        }
        b.append(DIV_CLOSE);
    }

    /** Couverture A4 illustree : image a fond perdu et cartouche contraste, sans etirer l'image. */
    private static void illustratedCover(StringBuilder b, Ctx ctx, CampaignJpaEntity c,
                                         CampaignPublication pub, PdfImage art) {
        double scale = Math.max(21.0 / art.w(), 29.7 / art.h());
        double width = art.w() * scale, height = art.h() * scale;
        b.append("<div class=\"cover cover-illustrated\"><img class=\"cover-background\" style=\"width:")
                .append(cm(width)).append(";height:").append(cm(height))
                .append(";left:").append(cm((21.0 - width) / 2))
                .append(";top:").append(cm((29.7 - height) / 2))
                .append("\" src=\"").append(art.uri()).append("\"/>")
                .append("<div class=\"cover-caption\">");
        String kicker = coverKicker(ctx, pub);
        if (kicker != null) b.append("<div class=\"cover-kicker\">").append(esc(kicker)).append(DIV_CLOSE);
        int titleSize = c.getName().length() > 100 ? 20 : c.getName().length() > 60 ? 26 : 32;
        b.append("<h1 class=\"cover-title\" style=\"font-size:").append(titleSize).append("pt\">")
                .append(esc(c.getName())).append("</h1>");
        if (notBlank(pub.subtitle())) b.append("<div class=\"cover-subtitle\">").append(esc(pub.subtitle())).append(DIV_CLOSE);
        b.append("<div class=\"cover-rule\"></div>");
        String meta = technicalLine(pub);
        if (!meta.isEmpty()) b.append("<div class=\"cover-meta\">").append(esc(meta)).append(DIV_CLOSE);
        if (!ctx.gm) b.append("<div class=\"cover-edition\">Édition joueurs</div>");
        if (notBlank(pub.author())) b.append("<div class=\"cover-author\">").append(esc(pub.author())).append(DIV_CLOSE);
        if (notBlank(pub.version())) b.append("<div class=\"cover-version\">Version ").append(esc(pub.version())).append(DIV_CLOSE);
        b.append("</div></div>");
    }

    /** Surtitre : le systeme de jeu s'il est renseigne ; sinon l'intitule historique du livret. */
    private static String coverKicker(Ctx ctx, CampaignPublication pub) {
        if (notBlank(pub.system())) return pub.system();
        if (pub.hasContent()) return null;
        return ctx.gm ? "Livret de campagne" : "Livret des joueurs";
    }

    /** "Niveaux : 1 à 3 · Joueurs : 3 à 5 · Durée : 2 séances" (morceaux renseignés seulement). */
    private static String technicalLine(CampaignPublication pub) {
        List<String> parts = new ArrayList<>();
        if (notBlank(pub.levels())) parts.add("Niveaux : " + pub.levels());
        if (notBlank(pub.players())) parts.add("Joueurs : " + pub.players());
        if (notBlank(pub.duration())) parts.add("Durée : " + pub.duration());
        return String.join("  ·  ", parts);
    }

    /**
     * Page de credits (verso de couverture) : titre, auteur, version, credits et mentions
     * legales — attendue dans tout module diffuse. Absente si rien n'est renseigne.
     */
    private static void creditsPage(StringBuilder b, CampaignJpaEntity c, CampaignPublication pub) {
        if (!pub.hasCredits()) return;
        b.append("<div class=\"credits-page\"><div class=\"credits-title\">").append(esc(c.getName())).append(DIV_CLOSE);
        if (notBlank(pub.subtitle())) {
            b.append("<div class=\"credits-subtitle\">").append(esc(pub.subtitle())).append(DIV_CLOSE);
        }
        block(b, "Auteur", pub.author());
        block(b, "Version", pub.version());
        block(b, "Crédits", pub.credits());
        if (notBlank(pub.legal())) {
            b.append("<div class=\"legal\">");
            prose(b, pub.legal());
            b.append(DIV_CLOSE);
        }
        b.append(DIV_CLOSE);
    }

    /**
     * Introduction d'un module publie (ordre attendu par les MJ qui l'achetent) : page
     * technique, argument, avertissements, puis — version MJ — contexte cache, accroches et
     * structure de l'aventure. Rien si la fiche Publication est vide (livret historique).
     */
    private void introduction(Ctx ctx, CampaignJpaEntity c, CampaignPublication pub, Structure st) {
        if (!pub.hasContent()) return;
        StringBuilder b = ctx.body;
        b.append("<h1 class=\"part\" id=\"part-intro\">Introduction</h1>");
        ctx.addToc(0, "Introduction", "part-intro");
        ctx.bookmarks.append(bookmark("Introduction", "part-intro", ""));

        ctx.beginColumns();
        technicalTable(b, pub);
        String pitch = notBlank(pub.pitch()) ? pub.pitch() : c.getDescription();
        introSection(b, "L'aventure en bref", pitch);
        box(b, "warning", "Avertissements de contenu", pub.contentWarnings());
        if (ctx.gm) {
            introSection(b, "Contexte pour le MJ", pub.gmBackground());
            introSection(b, "Accroches", pub.hooks());
            if (ctx.opt.includes(Section.NARRATIVE)) introSection(b, "Structure de l'aventure", structureOutline(st));
        }
        ctx.endColumns();
    }

    /** Tableau « Système / Niveaux / Joueurs / Durée » de la page technique. */
    private static void technicalTable(StringBuilder b, CampaignPublication pub) {
        if (!pub.hasTechnicalSheet()) return;
        b.append("<table class=\"stats-table tech-table\"><tbody>");
        int row = 0;
        row = techRow(b, row, "Système", pub.system());
        row = techRow(b, row, "Niveaux", pub.levels());
        row = techRow(b, row, "Joueurs", pub.players());
        techRow(b, row, "Durée", pub.duration());
        b.append(TBODY_TABLE_CLOSE);
    }

    private static int techRow(StringBuilder b, int row, String label, String value) {
        if (!notBlank(value)) return row;
        b.append(trTag(row)).append("<th>").append(esc(label)).append("</th><td>")
                .append(esc(value)).append(TD_TR_CLOSE);
        return row + 1;
    }

    /** Intertitre + texte (paragraphes, listes « - … ») ; rien si le texte est vide. */
    private static void introSection(StringBuilder b, String title, String text) {
        if (!notBlank(text)) return;
        b.append("<h2 class=\"section-head\">").append(esc(title)).append("</h2>");
        prose(b, text);
    }

    /** Plan de l'aventure : un arc par puce, suivi de ses chapitres. Null en mode plat. */
    private static String structureOutline(Structure st) {
        List<ArcJpaEntity> arcs = st.visibleArcs;
        if (arcs.isEmpty()) return null;
        if (arcs.size() == 1 && st.chaptersByArc.get(arcs.get(0).getId()).size() <= 1) return null;
        StringBuilder s = new StringBuilder();
        for (ArcJpaEntity arc : arcs) {
            List<String> chapters = new ArrayList<>();
            for (ChapterJpaEntity ch : st.chaptersByArc.get(arc.getId())) chapters.add(ch.getName());
            s.append("- ").append(arc.getName());
            if (!chapters.isEmpty()) s.append(" : ").append(String.join(", ", chapters));
            s.append('\n');
        }
        return s.toString();
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

        ctx.beginColumns();
        for (ArcJpaEntity arc : arcs) {
            arcSection(ctx, st, arc);
        }
        ctx.endColumns();
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
        arcFields(ctx, arc);

        for (ChapterJpaEntity ch : st.chaptersByArc.get(arc.getId())) {
            chapterSection(ctx, st, ch);
        }
        b.append(DIV_CLOSE);
    }

    /** Champs d'un arc ; tout est reserve au MJ (synopsis, enjeux, resolution...) sauf les illustrations. */
    private void arcFields(Ctx ctx, ArcJpaEntity arc) {
        illustrations(ctx, arc.getIllustrationImageIds());
        ctx.gmBlock(LABEL_DESCRIPTION, arc.getDescription());
        ctx.gmBox(CSS_SECRET, "Histoire cachée", arc.getHiddenTruth());
        ctx.gmBlock("Accroches", arc.getHooks());
        ctx.gmBlock(LABEL_THEMES, arc.getThemes());
        ctx.gmBlock(LABEL_ENJEUX, arc.getStakes());
        ctx.gmBlock(LABEL_RECOMPENSES, arc.getRewards());
        ctx.gmBlock(LABEL_RESOLUTION, arc.getResolution());
        ctx.gmBox(CSS_SECRET, LABEL_NOTES_MJ, arc.getGmNotes());
    }

    /** Un chapitre narratif complet (dans son arc) : en-tete + champs (fusionnes ou non) + scenes. */
    private void chapterSection(Ctx ctx, Structure st, ChapterJpaEntity ch) {
        StringBuilder b = ctx.body;
        QuestJpaEntity fused = st.questByContainerChapter.get(ch.getId());
        ctx.addToc(2, ch.getName(), "ch-" + ch.getId());
        b.append("<div class=\"chapter\"><div class=\"chapter-head\" id=\"ch-").append(ch.getId()).append("\">")
                .append("<span class=\"eyebrow\">").append(fused != null ? "Quête" : "Chapitre").append(SPAN_CLOSE)
                .append(esc(ch.getName())).append(DIV_CLOSE);
        chapterFields(ctx, ch, fused, st);

        for (SceneJpaEntity sc : st.scenesByChapter.get(ch.getId())) {
            scene(ctx, sc, st);
            ctx.scenes++;
        }
        b.append(DIV_CLOSE);
    }

    /**
     * Champs d'un chapitre ; si une quete est FUSIONNEE dessus (chapitre-conteneur), ses
     * champs sont integres au meme bloc — conditions de deblocage comprises — en dedupliquant
     * les textes identiques (les quetes converties du legacy dupliquent le chapitre).
     */
    private void chapterFields(Ctx ctx, ChapterJpaEntity ch, QuestJpaEntity fused, Structure st) {
        illustrations(ctx, ch.getIllustrationImageIds());
        if (fused == null) {
            ctx.gmBlock(LABEL_DESCRIPTION, ch.getDescription());
            block(ctx.body, LABEL_OBJECTIFS_JOUEURS, ch.getPlayerObjectives());
            ctx.gmBlock(LABEL_ENJEUX_NARRATIFS, ch.getNarrativeStakes());
            ctx.gmBox(CSS_SECRET, LABEL_NOTES_MJ, ch.getGmNotes());
            return;
        }
        illustrations(ctx, fused.getIllustrationImageIds());
        ctx.gmBlock(LABEL_DESCRIPTION, mergeField(ch.getDescription(), fused.getDescription()));
        ctx.gmBlock(LABEL_CONDITIONS, renderPrerequisites(fused.getPrerequisites(), st.questNames));
        if (ctx.gm) blockHtml(ctx.body, LABEL_NOEUDS, questNodeItems(fused, st));
        block(ctx.body, LABEL_OBJECTIFS_JOUEURS, mergeField(ch.getPlayerObjectives(), fused.getPlayerObjectives()));
        ctx.gmBlock(LABEL_ENJEUX_NARRATIFS, mergeField(ch.getNarrativeStakes(), fused.getNarrativeStakes()));
        ctx.gmBox(CSS_SECRET, LABEL_NOTES_MJ, mergeField(ch.getGmNotes(), fused.getGmNotes()));
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

        ctx.beginColumns();
        arcFields(ctx, arc);
        chapterFields(ctx, ch, st.questByContainerChapter.get(ch.getId()), st);

        StringBuilder scBms = new StringBuilder();
        for (SceneJpaEntity sc : st.scenesByChapter.get(ch.getId())) {
            ctx.addToc(1, sc.getName(), "scene-" + sc.getId());
            scBms.append(bookmark(sc.getName(), "scene-" + sc.getId(), ""));
            scene(ctx, sc, st);
            ctx.scenes++;
        }
        ctx.bookmarks.append(bookmark("Scènes", ANCHOR_PART_NARRATIVE, scBms.toString()));
        ctx.endColumns();
    }

    /**
     * Une scene. Version joueurs : seuls le contexte (lieu, moment), l'ambiance, le texte a
     * lire, les illustrations et les cartes restent — resume, secrets, consequences, sorties
     * et combats sont reserves au MJ.
     */
    private void scene(Ctx ctx, SceneJpaEntity sc, Structure st) {
        StringBuilder b = ctx.body;
        b.append("<div class=\"scene\" id=\"scene-").append(sc.getId()).append("\"><div class=\"scene-head\">")
                .append("<span class=\"eyebrow\">Scène</span>")
                .append(esc(sc.getName())).append(DIV_CLOSE);
        sceneMeta(b, sc);
        ctx.gmBlock("Résumé", sc.getDescription());
        ctx.gmBlock("Utilité", sc.getPurpose());
        block(b, "Ambiance", sc.getAtmosphere(), "ambiance");
        box(b, "readaloud", "À lire aux joueurs", sc.getPlayerNarration());
        ctx.gmBox(CSS_SECRET, "Notes secrètes MJ", sc.getGmSecretNotes());
        ctx.gmBlock("Choix et conséquences", sc.getChoicesConsequences());
        if (ctx.gm) blockHtml(b, "Sorties", sceneBranchItems(sc.getBranches(), st));
        // Combat : difficulte + ennemis de la rencontre (fiches du bestiaire en renvoi
        // « p. N » + texte libre) dans le meme encadre rouge.
        combatBox(ctx, "Combat", sc.getCombatDifficulty(), sc.getEnemyIds(), sc.getEnemies(), sc.getTactics(), st);
        illustrations(ctx, sc.getIllustrationImageIds());
        if (ctx.maps != null) {
            mapLinks(ctx, sc);
        } else {
            sceneBattlemaps(ctx, sc);
        }
        rooms(ctx, sc, st);
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

    /** Annexe Cartes demandee : la scene renvoie a ses cartes pleine page (« p. N »). */
    private static void mapLinks(Ctx ctx, SceneJpaEntity sc) {
        List<String> items = new ArrayList<>();
        String prefix = "map-" + sc.getId() + "-";
        for (PdfAppendices.MapPage p : ctx.maps.getOrDefault(sc.getId(), List.of())) {
            if (p.anchor().startsWith(prefix)) items.add(xref(p.anchor(), p.title()) + " " + pageRef(p.anchor()));
        }
        blockHtml(ctx.body, "Cartes", items);
    }

    /**
     * Battlemaps (images uniquement ; les videos ne sont pas rendables en PDF). Le libellé
     * de la variante (Jour/Nuit…) est rendu en légende sous l'image.
     */
    private void sceneBattlemaps(Ctx ctx, SceneJpaEntity sc) {
        if (sc.getBattlemaps() == null) return;
        for (var bm : sc.getBattlemaps()) {
            PdfImage battlemap = imageEncoder.fileImage(bm.mediaFileId());
            if (battlemap == null) continue;
            String caption = bm.label() == null || bm.label().isBlank()
                    ? "Battlemap" : "Battlemap — " + bm.label();
            illustration(ctx, battlemap, caption);
        }
    }

    /**
     * Pieces explorables de la scene (donjon, crypte...) — sous-blocs ambres. Version joueurs :
     * nom, plan et illustrations seulement.
     */
    private void rooms(Ctx ctx, SceneJpaEntity sc, Structure st) {
        StringBuilder b = ctx.body;
        if (sc.getRooms() == null || sc.getRooms().isEmpty()) return;
        List<Room> rooms = new ArrayList<>(sc.getRooms());
        rooms.sort(java.util.Comparator.comparingInt(Room::getOrder));
        Map<String, String> roomNames = new HashMap<>();
        for (Room r : rooms) roomNames.put(r.getId(), r.getName());

        for (Room r : rooms) {
            b.append("<div class=\"room\"><div class=\"room-head\"><span class=\"eyebrow\">Pièce");
            if (r.getFloor() != null) b.append(" · Étage ").append(r.getFloor());
            b.append(SPAN_CLOSE).append(esc(r.getName())).append(DIV_CLOSE);
            ctx.gmBlock(LABEL_DESCRIPTION, r.getDescription());
            combatBox(ctx, "Ennemis", null, r.getEnemyIds(), r.getEnemies(), null, st);
            ctx.gmBlock("Butin", r.getLoot());
            ctx.gmBlock("Pièges", r.getTraps());
            ctx.gmBox(CSS_SECRET, LABEL_NOTES_MJ, r.getGmNotes());
            roomMap(ctx, sc, r);
            illustrations(ctx, r.getIllustrationImageIds());
            if (ctx.gm && r.getBranches() != null && !r.getBranches().isEmpty()) {
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

    /** Plan d'une piece : en annexe pleine page si demandee (renvoi), sinon en illustration. */
    private void roomMap(Ctx ctx, SceneJpaEntity sc, Room r) {
        if (ctx.maps != null) {
            String anchor = PdfAppendices.roomMapAnchor(sc.getId(), r.getId());
            boolean inAnnex = ctx.maps.getOrDefault(sc.getId(), List.of()).stream()
                    .anyMatch(p -> p.anchor().equals(anchor));
            if (inAnnex) blockHtml(ctx.body, "Plan", List.of("carte en annexe " + pageRef(anchor)));
            return;
        }
        PdfImage map = imageEncoder.image(r.getMapImageId(), PdfImageEncoder.ILLUSTRATION_MAX);
        if (map != null) illustration(ctx, map, "Plan — " + r.getName());
    }

    /**
     * Sorties narratives d'une scene en HTML : nom de la scene cible cliquable et sa page
     * (« Vers « La crypte » (p. 12) — Si les PJ fouillent [indice] »).
     */
    private static List<String> sceneBranchItems(List<SceneBranch> branches, Structure st) {
        List<String> items = new ArrayList<>();
        if (branches == null) return items;
        for (SceneBranch br : branches) {
            String anchor = "scene-" + br.targetSceneId();
            StringBuilder line = new StringBuilder("Vers « ")
                    .append(xref(anchor, st.sceneNames.getOrDefault(br.targetSceneId(), "?")))
                    .append(" » ").append(pageRef(anchor));
            if (notBlank(br.label())) line.append(" — ").append(esc(br.label().trim()));
            String kind = switch (br.kind()) {
                case CLUE -> "indice";
                case LEAD -> "piste";
                case EXIT -> null;
            };
            if (kind != null) line.append(" [").append(kind).append("]");
            if (notBlank(br.condition())) line.append(" (condition : ").append(esc(br.condition().trim())).append(")");
            items.add(line.toString());
        }
        return items;
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
     * Encadre de combat (reserve au MJ) : difficulte, adversaires du bestiaire en renvoi vers
     * leur fiche (« Bandit (p. 24) »), puis texte libre. Rien si tout est vide.
     */
    private static void combatBox(Ctx ctx, String label, String difficulty, List<String> enemyIds,
                                  String freeText, String tactics, Structure st) {
        if (!ctx.gm) return;
        List<String> enemies = new ArrayList<>();
        if (enemyIds != null) {
            for (String id : enemyIds) {
                String name = st.enemyNames.get(id);
                if (name != null) enemies.add(xref("enemy-" + id, name) + " " + pageRef("enemy-" + id));
            }
        }
        if (!notBlank(difficulty) && enemies.isEmpty() && !notBlank(freeText) && !notBlank(tactics)) return;
        StringBuilder b = ctx.body;
        String boxText = Objects.toString(difficulty, "") + "\n" + String.join("\n", enemies)
                + "\n" + Objects.toString(freeText, "") + "\n" + Objects.toString(tactics, "");
        b.append("<div class=\"box combat").append(isLongBox(boxText) ? " flow" : "")
                .append("\"><div class=\"box-label\">").append(esc(label)).append(DIV_CLOSE);
        prose(b, difficulty);
        if (!enemies.isEmpty()) {
            b.append("<ul class=\"prose-list\">");
            for (String e : enemies) b.append("<li>").append(e).append("</li>");
            b.append("</ul>");
        }
        prose(b, freeText);
        block(b, "Tactique et moral", tactics);
        b.append(DIV_CLOSE);
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

        ctx.beginColumns();
        for (QuestJpaEntity q : quests) {
            ctx.addToc(1, q.getName(), "quest-" + q.getId());
            standaloneQuest(ctx, q, st);
        }
        ctx.endColumns();
    }

    /** Une quete libre/transversale : ses champs + les scenes de ses conteneurs (arc SYSTEM masque). */
    private void standaloneQuest(Ctx ctx, QuestJpaEntity q, Structure st) {
        StringBuilder b = ctx.body;
        b.append("<div class=\"chapter\"><div class=\"chapter-head\" id=\"quest-").append(q.getId())
                .append("\"><span class=\"eyebrow\">Quête</span>").append(esc(q.getName())).append(DIV_CLOSE);
        illustrations(ctx, q.getIllustrationImageIds());
        ctx.gmBlock(LABEL_DESCRIPTION, q.getDescription());
        ctx.gmBlock(LABEL_CONDITIONS, renderPrerequisites(q.getPrerequisites(), st.questNames));
        if (ctx.gm) blockHtml(ctx.body, LABEL_NOEUDS, questNodeItems(q, st));
        block(b, LABEL_OBJECTIFS_JOUEURS, q.getPlayerObjectives());
        ctx.gmBlock(LABEL_ENJEUX_NARRATIFS, q.getNarrativeStakes());
        ctx.gmBox(CSS_SECRET, LABEL_NOTES_MJ, q.getGmNotes());
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
                scene(ctx, sc, st);
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
    private static List<String> questNodeItems(QuestJpaEntity q, Structure st) {
        List<String> items = new ArrayList<>();
        if (q.getNodes() == null) return items;
        Set<String> containers = st.containerChapterIds(q);
        for (QuestNodeRef n : q.getNodes()) {
            if (n.nodeType() == NodeType.SCENE) {
                String anchor = "scene-" + n.nodeId();
                items.add("Scène : " + xref(anchor, st.sceneNames.getOrDefault(n.nodeId(), "?")) + " " + pageRef(anchor));
            } else if (!containers.contains(n.nodeId())) {
                String anchor = "ch-" + n.nodeId();
                items.add("Chapitre : " + xref(anchor, st.chapterNames.getOrDefault(n.nodeId(), "?")) + " " + pageRef(anchor));
            }
        }
        return items;
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
    record PersonaRow(Long id, int order, String name, String folder, String level, String portraitId,
                              Map<String, String> values, Map<String, Map<String, String>> keyValueValues,
                              Map<String, List<String>> imageValues, Map<String, String> foundryStats,
                              NpcStory story) {}

    private List<PersonaRow> npcEntries(CampaignJpaEntity c) {
        List<PersonaRow> out = new ArrayList<>();
        for (NpcJpaEntity n : npcRepo.findByCampaignIdOrderByOrderAsc(c.getId())) {
            out.add(new PersonaRow(n.getId(), n.getOrder(), n.getName(), n.getFolder(), null, n.getPortraitImageId(),
                    n.getValues(), n.getKeyValueValues(), n.getImageValues(), null, n.getStory()));
        }
        return out;
    }

    private List<PersonaRow> enemyEntries(CampaignJpaEntity c) {
        List<PersonaRow> out = new ArrayList<>();
        for (EnemyJpaEntity e : enemyRepo.findByCampaignIdOrderByOrderAsc(c.getId())) {
            out.add(new PersonaRow(e.getId(), e.getOrder(), e.getName(), e.getFolder(), e.getLevel(), e.getPortraitImageId(),
                    e.getValues(), e.getKeyValueValues(), e.getImageValues(), e.getFoundryStats(), null));
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
        for (PersonaRow r : rows) personaCard(ctx, r, template, enemy);
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
        for (PersonaRow r : ungrouped) personaCard(ctx, r, template, enemy);
    }

    private void personaCard(Ctx ctx, PersonaRow r, List<TemplateField> template, boolean enemy) {
        StringBuilder b = ctx.body;
        // Mise en page en TABLE (portrait | contenu) : openhtmltopdf gere mal le float
        // + overflow (le texte se superposait au portrait).
        b.append("<div class=\"card\" id=\"").append(enemy ? "enemy-" : "npc-").append(r.id())
                .append("\"><table class=\"persona\"><tr>");
        PdfImage portrait = imageEncoder.image(r.portraitId(), PdfImageEncoder.PORTRAIT_MAX);
        if (portrait != null) {
            double widthCm = Math.min(3.0, PORTRAIT_MAX_HEIGHT_CM * portrait.w() / portrait.h());
            b.append("<td class=\"persona-portrait\"><img style=\"width:").append(cm(widthCm))
                    .append("\" src=\"").append(portrait.uri()).append("\"/></td>");
        }
        b.append("<td class=\"persona-content\">");
        b.append("<div class=\"persona-name\">").append(esc(r.name()));
        if (notBlank(r.level())) b.append(" <span class=\"level\">Niv. ").append(esc(r.level())).append(SPAN_CLOSE);
        String role = r.story() != null ? ROLE_LABELS.get(r.story().role()) : null;
        if (ctx.gm && role != null) b.append(" <span class=\"level\">").append(role).append(SPAN_CLOSE);
        b.append(DIV_CLOSE);
        if (r.story() != null) {
            block(b, "Signe distinctif", r.story().mannerism());
            ctx.gmBlock("Motivation", r.story().motivation());
            ctx.gmBlock("Ce qu'il sait", r.story().knowledge());
        }

        renderFields(ctx, template, r.values(), r.keyValueValues(), r.imageValues(), null);

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

    /** Libelles des roles d'un PNJ dans l'histoire (fiche « role dans l'histoire »). */
    private static final Map<String, String> ROLE_LABELS = Map.of(
            "OPPONENT", "Opposant", "ALLY", "Allié", "RESOURCE", "Ressource", "EXTRA", "Figurant");

    /** Valeurs de stats considerees comme "vides"/bruit (masquees dans le livret). */
    private static final Set<String> STAT_NOISE_VALUES = Set.of("0", "0.0", "false", "none", "null", "", "-", "—");
    /** Premier segment de chemin a elaguer du libelle (purement structurel). */
    private static final Set<String> STAT_DROP_PREFIX = Set.of("attributes", "details", "system", "data");
    /** Segments sans information pour un lecteur ("hp value" -> "PV"). */
    private static final Set<String> STAT_DROP_SEGMENTS = Set.of("attributes", "abilities", "details",
            "system", "data", "value", "traits", "skills");
    /**
     * Abreviations et termes courants des systemes Foundry (dnd5e, pf2e...) traduits pour
     * un livret francais ; un segment inconnu garde son libelle humanise.
     */
    private static final Map<String, String> STAT_TERMS = Map.ofEntries(
            Map.entry("hp", "PV"), Map.entry("ac", "CA"), Map.entry("cr", "FP"),
            Map.entry("str", "FOR"), Map.entry("dex", "DEX"), Map.entry("con", "CON"),
            Map.entry("int", "INT"), Map.entry("wis", "SAG"), Map.entry("cha", "CHA"),
            Map.entry("max", "max"), Map.entry("temp", "temp."), Map.entry("level", "niveau"),
            Map.entry("xp", "XP"), Map.entry("init", "initiative"), Map.entry("prof", "maîtrise"),
            Map.entry("movement", "déplacement"), Map.entry("speed", "vitesse"),
            Map.entry("walk", "marche"), Map.entry("fly", "vol"), Map.entry("swim", "nage"),
            Map.entry("climb", "escalade"), Map.entry("burrow", "fouissement"),
            Map.entry("senses", "sens"), Map.entry("darkvision", "vision dans le noir"),
            Map.entry("languages", "langues"), Map.entry("alignment", "alignement"),
            Map.entry("type", "type"), Map.entry("size", "taille"), Map.entry("perception", "perception"),
            Map.entry("spelldc", "DD des sorts"), Map.entry("dc", "DD"), Map.entry("creaturetype", "type de créature"));

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

    /**
     * "attributes.hp.value" -> "PV" ; "abilities.str.value" -> "FOR" ; "attributes.hp.max" ->
     * "PV max" ; "details.cr" -> "FP" ; segment inconnu : camelCase humanise ("Creature type").
     */
    static String humanizeStatKey(String key) {
        String[] parts = key.split("\\.");
        int start = (parts.length > 1 && STAT_DROP_PREFIX.contains(parts[0].toLowerCase())) ? 1 : 0;
        List<String> words = new ArrayList<>();
        for (String part : Arrays.asList(parts).subList(start, parts.length)) {
            String lower = part.toLowerCase(Locale.ROOT);
            if (STAT_DROP_SEGMENTS.contains(lower) && parts.length - start > 1) continue;
            String term = STAT_TERMS.get(lower);
            words.add(term != null ? term
                    : part.replaceAll("([a-z0-9])([A-Z])", "$1 $2").toLowerCase(Locale.ROOT).trim()); // camelCase -> mots
        }
        String s = String.join(" ", words).trim();
        if (s.isEmpty()) return key;
        return Character.toUpperCase(s.charAt(0)) + s.substring(1);
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

    private void lore(Ctx ctx, CampaignJpaEntity campaign, Structure st) {
        String loreId = campaign.getLoreId();
        if (loreId == null || loreId.isBlank()) return;
        Long lid;
        try { lid = Long.parseLong(loreId); } catch (NumberFormatException ex) { return; }

        List<PageJpaEntity> pages = new ArrayList<>(pageRepo.findByLoreId(lid));
        if (ctx.opt.loreScope() == LoreScope.LINKED) {
            Set<String> linked = linkedPageIds(campaign, st);
            pages.removeIf(p -> !linked.contains(String.valueOf(p.getId())));
        }
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
                b.append("<div class=\"card flow\"><div class=\"card-body\"><div class=\"persona-name\">")
                        .append(esc(p.getTitle())).append(DIV_CLOSE);
                List<TemplateField> tpl = p.getTemplateId() != null
                        ? fieldsOf(templates.get(p.getTemplateId())) : null;
                renderFields(ctx, tpl, p.getValues(), p.getKeyValueValues(), p.getImageValues(), p.getTableValues());
                b.append("</div></div>");
            }
        }
        ctx.bookmarks.append(bookmark("Lore", "part-lore", fBms.toString()));
    }

    /**
     * Pages de lore citees par le contenu de la campagne (pages liees des arcs, chapitres,
     * scenes, quetes et PNJ) : l'export « pages liees » evite de livrer tout l'univers,
     * secrets des autres campagnes compris.
     */
    private Set<String> linkedPageIds(CampaignJpaEntity campaign, Structure st) {
        Set<String> ids = new HashSet<>();
        for (ArcJpaEntity a : st.arcs) addAllNonNull(ids, a.getRelatedPageIds());
        for (List<ChapterJpaEntity> chs : st.chaptersByArc.values()) {
            for (ChapterJpaEntity ch : chs) addAllNonNull(ids, ch.getRelatedPageIds());
        }
        for (List<SceneJpaEntity> scs : st.scenesByChapter.values()) {
            for (SceneJpaEntity sc : scs) addAllNonNull(ids, sc.getRelatedPageIds());
        }
        for (QuestJpaEntity q : st.quests) addAllNonNull(ids, q.getRelatedPageIds());
        for (NpcJpaEntity n : npcRepo.findByCampaignIdOrderByOrderAsc(campaign.getId())) {
            addAllNonNull(ids, n.getRelatedPageIds());
        }
        return ids;
    }

    private static void addAllNonNull(Set<String> into, List<String> values) {
        if (values != null) into.addAll(values);
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

    private void renderFields(Ctx ctx, List<TemplateField> template, Map<String, String> values,
                              Map<String, Map<String, String>> keyValueValues,
                              Map<String, List<String>> imageValues,
                              Map<String, List<Map<String, String>>> tableValues) {
        StringBuilder b = ctx.body;
        // Repli : pas de template -> paires brutes cle=valeur.
        if (template == null || template.isEmpty()) {
            if (values != null) {
                for (Map.Entry<String, String> e : values.entrySet()) block(b, e.getKey(), e.getValue());
            }
            return;
        }
        for (TemplateField f : template) {
            if (f == null || f.getName() == null || f.getType() == null) continue;
            renderField(ctx, f, values, keyValueValues, imageValues, tableValues);
        }
    }

    private void renderField(Ctx ctx, TemplateField f, Map<String, String> values,
                              Map<String, Map<String, String>> keyValueValues,
                              Map<String, List<String>> imageValues,
                              Map<String, List<Map<String, String>>> tableValues) {
        StringBuilder b = ctx.body;
        switch (f.getType()) {
            case TEXT, NUMBER -> block(b, f.getName(), values != null ? values.get(f.getName()) : null);
            case KEY_VALUE_LIST -> renderKeyValueField(b, f, keyValueValues);
            case IMAGE -> illustrations(ctx, imageValues != null ? imageValues.get(f.getName()) : null);
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
    private void illustrations(Ctx ctx, List<String> imageIds) {
        if (imageIds == null) return;
        for (String id : imageIds) {
            PdfImage img = imageEncoder.image(id, PdfImageEncoder.ILLUSTRATION_MAX);
            if (img != null) illustration(ctx, img, null);
        }
    }

    /**
     * Une illustration centree, avec legende optionnelle. La largeur est calculee pour que
     * la hauteur affichee reste bornee : une image tres verticale ne mange plus la page.
     */
    private void illustration(Ctx ctx, PdfImage img, String caption) {
        StringBuilder b = ctx.body;
        // Hauteur bornee proportionnellement au format (A5 : environ 7,8 cm).
        double maxHeight = ILLUSTRATION_MAX_HEIGHT_CM * ctx.opt.format().widthCm / PdfExportOptions.PageFormat.A4.widthCm;
        // Reserve les retraits des encadres et pieces imbriques, y compris en deux colonnes.
        double maxWidth = ctx.contentWidthCm() - (ctx.columns ? 1.2 : 0);
        double widthCm = Math.min(maxWidth, maxHeight * img.w() / img.h());
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

    /**
     * CSS final : en-tete courant (nom de la campagne), format de page (taille, marges,
     * corps du texte) et, en mode impression, la feuille sans aplats ajoutee en dernier.
     */
    // S125 : faux positif — commentaire explicatif (prose), pas de code mort.
    @SuppressWarnings("java:S125")
    private static String cssFor(String campaignName, PdfExportOptions opt) {
        String header = campaignName == null ? "" : campaignName.trim();
        if (header.length() > 70) header = header.substring(0, 69) + "…";
        PdfExportOptions.PageFormat f = opt.format();
        String margins = cm(f.marginTopCm) + " " + cm(f.marginSideCm) + " " + cm(f.marginBottomCm);
        // Echappement CSS (chaine entre quotes) PUIS XML : le CSS vit dans <style>,
        // du PCDATA — un '&' ou '<' brut casserait le parse du document entier.
        String css = CSS.replace("__HEADER__", esc(header.replace("\\", "\\\\").replace("'", "\\'")))
                .replace("__PAGE_SIZE__", f.cssSize)
                .replace("__PAGE_MARGIN__", margins)
                .replace("__BODY_PT__", String.format(Locale.ROOT, "%.1fpt", f.bodyPt));
        if (f == PdfExportOptions.PageFormat.A5) css += A5_CSS;
        if (f == PdfExportOptions.PageFormat.A4) {
            css += EDITORIAL_CSS.replace("__HEADER__", esc(header.replace("\\", "\\\\").replace("'", "\\'")));
        }
        return opt.printFriendly() ? css + PRINT_CSS : css;
    }

    // CSS print (CSS 2.1 + paged media supporte par openhtmltopdf : pas de flexbox/grid).
    // Polices de base PDF : serif (Times) pour le texte courant — plus lisible en long —,
    // sans-serif (Helvetica) pour titres, libelles et tables.
    // ATTENTION : ce CSS vit dans <style> (PCDATA XML) — jamais de '&' ici, meme en commentaire.
    // Contenu dans resources/pdf/export.css (extrait pour alleger cette classe).
    // S125 : faux positif — le bloc ci-dessus est de la prose explicative, pas du code mort.
    @SuppressWarnings("java:S125")
    private static final String CSS = loadCss("/pdf/export.css");
    /** Surcharges « impression » : fonds blancs, filets conserves (economie d'encre). */
    private static final String PRINT_CSS = loadCss("/pdf/export-print.css");
    /** Surcharges A5 : couverture et titres resserres pour une page etroite. */
    private static final String A5_CSS = loadCss("/pdf/export-a5.css");
    private static final String EDITORIAL_CSS = loadCss("/pdf/export-editorial.css");

    private static String loadCss(String resource) {
        try (InputStream in = PdfExportService.class.getResourceAsStream(resource)) {
            if (in == null) {
                throw new IllegalStateException("Ressource " + resource + " introuvable dans le classpath");
            }
            return new String(in.readAllBytes(), StandardCharsets.UTF_8);
        } catch (IOException e) {
            throw new UncheckedIOException("Impossible de charger le CSS d'export PDF", e);
        }
    }
}
