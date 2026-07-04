package com.loremind.infrastructure.transfer.pdf;

import com.loremind.domain.campaigncontext.ArcType;
import com.loremind.domain.campaigncontext.LinkType;
import com.loremind.domain.campaigncontext.NodeType;
import com.loremind.domain.campaigncontext.Prerequisite;
import com.loremind.domain.campaigncontext.QuestNodeRef;
import com.loremind.domain.campaigncontext.Room;
import com.loremind.domain.campaigncontext.RoomBranch;
import com.loremind.domain.campaigncontext.SceneBranch;
import com.loremind.domain.files.ports.FileStorage;
import com.loremind.domain.images.ports.ImageStorage;
import com.loremind.domain.shared.template.FieldType;
import com.loremind.domain.shared.template.TemplateField;
import com.loremind.infrastructure.persistence.entity.*;
import com.loremind.infrastructure.persistence.jpa.*;
import com.openhtmltopdf.outputdevice.helper.BaseRendererBuilder;
import com.openhtmltopdf.pdfboxout.PdfRendererBuilder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.time.LocalDate;
import java.time.format.DateTimeFormatter;
import java.util.*;

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

    /** Cotes max (px) avant re-encodage : portraits compacts, battlemaps/illustrations larges. */
    private static final int PORTRAIT_MAX = 700;
    private static final int ILLUSTRATION_MAX = 1500;

    /** Largeur utile d'une page A4 (21cm - 2 x 1.7cm de marges). */
    private static final double CONTENT_WIDTH_CM = 17.6;
    /** Hauteur max affichee d'une illustration : evite qu'une image portrait mange la page. */
    private static final double ILLUSTRATION_MAX_HEIGHT_CM = 11.0;
    /** Hauteur max affichee d'un portrait de fiche (colonne de 3cm de large). */
    private static final double PORTRAIT_MAX_HEIGHT_CM = 4.5;

    private static final DateTimeFormatter FR_DATE = DateTimeFormatter.ofPattern("d MMMM yyyy", Locale.FRENCH);

    private final CampaignJpaRepository campaignRepo;
    private final ArcJpaRepository arcRepo;
    private final ChapterJpaRepository chapterRepo;
    private final SceneJpaRepository sceneRepo;
    private final QuestJpaRepository questRepo;
    private final NpcJpaRepository npcRepo;
    private final EnemyJpaRepository enemyRepo;
    private final RandomTableJpaRepository randomTableRepo;
    private final GameSystemJpaRepository gameSystemRepo;
    private final ImageJpaRepository imageRepo;
    private final StoredFileJpaRepository storedFileRepo;
    private final LoreNodeJpaRepository loreNodeRepo;
    private final PageJpaRepository pageRepo;
    private final TemplateJpaRepository templateRepo;
    private final ImageStorage imageStorage;
    private final FileStorage fileStorage;

    public PdfExportService(CampaignJpaRepository campaignRepo, ArcJpaRepository arcRepo,
                            ChapterJpaRepository chapterRepo, SceneJpaRepository sceneRepo,
                            QuestJpaRepository questRepo,
                            NpcJpaRepository npcRepo, EnemyJpaRepository enemyRepo,
                            RandomTableJpaRepository randomTableRepo,
                            GameSystemJpaRepository gameSystemRepo, ImageJpaRepository imageRepo,
                            StoredFileJpaRepository storedFileRepo,
                            LoreNodeJpaRepository loreNodeRepo, PageJpaRepository pageRepo,
                            TemplateJpaRepository templateRepo, ImageStorage imageStorage,
                            FileStorage fileStorage) {
        this.campaignRepo = campaignRepo;
        this.arcRepo = arcRepo;
        this.chapterRepo = chapterRepo;
        this.sceneRepo = sceneRepo;
        this.questRepo = questRepo;
        this.npcRepo = npcRepo;
        this.enemyRepo = enemyRepo;
        this.randomTableRepo = randomTableRepo;
        this.gameSystemRepo = gameSystemRepo;
        this.imageRepo = imageRepo;
        this.storedFileRepo = storedFileRepo;
        this.loreNodeRepo = loreNodeRepo;
        this.pageRepo = pageRepo;
        this.templateRepo = templateRepo;
        this.imageStorage = imageStorage;
        this.fileStorage = fileStorage;
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
        font(builder, "/fonts/DejaVuSerif.ttf", "DejaVu Serif", 400, BaseRendererBuilder.FontStyle.NORMAL);
        font(builder, "/fonts/DejaVuSerif-Bold.ttf", "DejaVu Serif", 700, BaseRendererBuilder.FontStyle.NORMAL);
        font(builder, "/fonts/DejaVuSerif-Italic.ttf", "DejaVu Serif", 400, BaseRendererBuilder.FontStyle.ITALIC);
        font(builder, "/fonts/DejaVuSerif-BoldItalic.ttf", "DejaVu Serif", 700, BaseRendererBuilder.FontStyle.ITALIC);
        font(builder, "/fonts/DejaVuSans.ttf", "DejaVu Sans", 400, BaseRendererBuilder.FontStyle.NORMAL);
        font(builder, "/fonts/DejaVuSans-Bold.ttf", "DejaVu Sans", 700, BaseRendererBuilder.FontStyle.NORMAL);
        font(builder, "/fonts/DejaVuSans-Oblique.ttf", "DejaVu Sans", 400, BaseRendererBuilder.FontStyle.ITALIC);
        font(builder, "/fonts/DejaVuSans-BoldOblique.ttf", "DejaVu Sans", 700, BaseRendererBuilder.FontStyle.ITALIC);
    }

    private void font(PdfRendererBuilder builder, String resource, String family,
                      int weight, BaseRendererBuilder.FontStyle style) {
        if (PdfExportService.class.getResource(resource) == null) {
            log.warn("Police absente du classpath, repli polices de base : {}", resource);
            return;
        }
        builder.useFont(() -> PdfExportService.class.getResourceAsStream(resource), family, weight, style, true);
    }

    // ====================================================================== Structure

    /**
     * Structure narrative de la campagne chargee UNE fois : arcs/chapitres/scenes/quetes
     * tries, index de noms, et liens quete -&gt; chapitre-conteneur (fusion de l'arbre).
     */
    private static final class Structure {
        List<ArcJpaEntity> arcs = List.of();
        /** Arcs affiches en narration (l'arc technique SYSTEM est de la plomberie). */
        List<ArcJpaEntity> visibleArcs = List.of();
        final Map<Long, List<ChapterJpaEntity>> chaptersByArc = new LinkedHashMap<>();
        final Map<Long, List<SceneJpaEntity>> scenesByChapter = new LinkedHashMap<>();
        List<QuestJpaEntity> quests = List.of();
        /** Chapitre-conteneur -&gt; quete fusionnee dessus (jumeau hub ou conteneur SYSTEM). */
        final Map<Long, QuestJpaEntity> questByContainerChapter = new LinkedHashMap<>();
        /** Quetes SANS conteneur dans la narration visible : rendues dans la partie « Quetes ». */
        final List<QuestJpaEntity> standaloneQuests = new ArrayList<>();
        final Map<String, String> chapterNames = new HashMap<>();
        final Map<String, String> sceneNames = new HashMap<>();
        final Map<String, String> questNames = new HashMap<>();
        /** Noms des fiches du bestiaire — resout les {@code enemyIds} des scenes/pieces. */
        final Map<String, String> enemyNames = new HashMap<>();

        /** Ids (String) des chapitres-conteneurs de cette quete. */
        Set<String> containerChapterIds(QuestJpaEntity q) {
            Set<String> out = new HashSet<>();
            for (Map.Entry<Long, QuestJpaEntity> e : questByContainerChapter.entrySet()) {
                if (Objects.equals(e.getValue().getId(), q.getId())) out.add(String.valueOf(e.getKey()));
            }
            return out;
        }
    }

    private Structure loadStructure(CampaignJpaEntity campaign) {
        Structure st = new Structure();
        st.arcs = sortByOrder(arcRepo.findByCampaignId(campaign.getId()), ArcJpaEntity::getOrder);
        List<ArcJpaEntity> visible = new ArrayList<>();
        Set<Long> systemArcIds = new HashSet<>();
        for (ArcJpaEntity arc : st.arcs) {
            if (arc.getType() == ArcType.SYSTEM) systemArcIds.add(arc.getId());
            else visible.add(arc);
        }
        st.visibleArcs = visible;

        Map<Long, Long> arcOfChapter = new HashMap<>();
        for (ArcJpaEntity arc : st.arcs) {
            List<ChapterJpaEntity> chapters = sortByOrder(chapterRepo.findByArcId(arc.getId()), ChapterJpaEntity::getOrder);
            st.chaptersByArc.put(arc.getId(), chapters);
            for (ChapterJpaEntity ch : chapters) {
                arcOfChapter.put(ch.getId(), arc.getId());
                st.chapterNames.put(String.valueOf(ch.getId()), ch.getName());
                List<SceneJpaEntity> scenes = sortByOrder(sceneRepo.findByChapterId(ch.getId()), SceneJpaEntity::getOrder);
                st.scenesByChapter.put(ch.getId(), scenes);
                for (SceneJpaEntity sc : scenes) st.sceneNames.put(String.valueOf(sc.getId()), sc.getName());
            }
        }

        for (EnemyJpaEntity e : enemyRepo.findByCampaignIdOrderByOrderAsc(campaign.getId())) {
            st.enemyNames.put(String.valueOf(e.getId()), e.getName());
        }

        st.quests = sortByOrder(questRepo.findByCampaignId(campaign.getId()), QuestJpaEntity::getOrder);
        for (QuestJpaEntity q : st.quests) st.questNames.put(String.valueOf(q.getId()), q.getName());
        // Meme regle que QuestService.isContainerOf : un chapitre reference est le CONTENEUR
        // de la quete s'il vit dans l'arc de la quete (jumeau hub) ou dans l'arc SYSTEM.
        for (QuestJpaEntity q : st.quests) {
            boolean fusedInNarrative = false;
            if (q.getNodes() != null) {
                for (QuestNodeRef n : q.getNodes()) {
                    if (n.nodeType() != NodeType.CHAPTER) continue;
                    Long cid;
                    try { cid = Long.parseLong(n.nodeId()); } catch (NumberFormatException ex) { continue; }
                    Long arcId = arcOfChapter.get(cid);
                    if (arcId == null) continue;
                    boolean container = (q.getArcId() != null && q.getArcId().equals(arcId))
                            || systemArcIds.contains(arcId);
                    if (!container) continue;
                    st.questByContainerChapter.putIfAbsent(cid, q);
                    if (st.questByContainerChapter.get(cid) == q && !systemArcIds.contains(arcId)) {
                        fusedInNarrative = true;
                    }
                }
            }
            if (!fusedInNarrative) st.standaloneQuests.add(q);
        }
        return st;
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
        int scenes, quests, npcs, enemies, randomTables, lorePages;

        void toc(int level, String title, String anchor) {
            toc.add(new TocEntry(level, title, anchor));
        }
    }

    private String buildXhtml(CampaignJpaEntity campaign) {
        List<TemplateField> npcTemplate = resolveTemplate(campaign.getGameSystemId(), true);
        List<TemplateField> enemyTemplate = resolveTemplate(campaign.getGameSystemId(), false);

        // Les sections d'abord (elles remplissent sommaire + compteurs), la couverture
        // et le sommaire sont assembles ensuite en tete de document.
        Structure st = loadStructure(campaign);
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
            b.append("<div class=\"cover-meta\">").append(esc(meta)).append("</div>");
        }
        if (notBlank(c.getDescription())) {
            b.append("<div class=\"cover-desc\">").append(multiline(c.getDescription())).append("</div>");
        }
        b.append("<div class=\"cover-date\">Exporté le ").append(esc(LocalDate.now().format(FR_DATE)))
                .append("</div>");
        b.append("</div>");
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

        StringBuilder b = ctx.body;
        b.append("<h1 class=\"part\" id=\"part-narrative\">Structure narrative</h1>");
        ctx.toc(0, "Structure narrative", "part-narrative");

        StringBuilder arcBms = new StringBuilder();
        for (ArcJpaEntity arc : arcs) {
            StringBuilder chBms = new StringBuilder();
            for (ChapterJpaEntity ch : st.chaptersByArc.get(arc.getId())) {
                chBms.append(bookmark(ch.getName(), "ch-" + ch.getId(), ""));
            }
            arcBms.append(bookmark(arc.getName(), "arc-" + arc.getId(), chBms.toString()));
        }
        ctx.bookmarks.append(bookmark("Structure narrative", "part-narrative", arcBms.toString()));

        for (ArcJpaEntity arc : arcs) {
            ctx.toc(1, arc.getName(), "arc-" + arc.getId());
            b.append("<div class=\"arc\"><h2 class=\"arc-head\" id=\"arc-").append(arc.getId()).append("\">")
                    .append("<span class=\"eyebrow eyebrow-light\">Arc</span>")
                    .append(esc(arc.getName())).append("</h2>");
            illustrations(b, arc.getIllustrationImageIds());
            block(b, "Description", arc.getDescription());
            block(b, "Themes", arc.getThemes());
            block(b, "Enjeux", arc.getStakes());
            block(b, "Recompenses", arc.getRewards());
            block(b, "Resolution", arc.getResolution());
            box(b, "secret", "Notes MJ", arc.getGmNotes());

            for (ChapterJpaEntity ch : st.chaptersByArc.get(arc.getId())) {
                QuestJpaEntity fused = st.questByContainerChapter.get(ch.getId());
                ctx.toc(2, ch.getName(), "ch-" + ch.getId());
                b.append("<div class=\"chapter\"><div class=\"chapter-head\" id=\"ch-").append(ch.getId()).append("\">")
                        .append("<span class=\"eyebrow\">").append(fused != null ? "Quête" : "Chapitre").append("</span>")
                        .append(esc(ch.getName())).append("</div>");
                chapterFields(b, ch, fused, st);

                for (SceneJpaEntity sc : st.scenesByChapter.get(ch.getId())) {
                    scene(b, sc, st);
                    ctx.scenes++;
                }
                b.append("</div>");
            }
            b.append("</div>");
        }
    }

    /**
     * Champs d'un chapitre ; si une quete est FUSIONNEE dessus (chapitre-conteneur), ses
     * champs sont integres au meme bloc — conditions de deblocage comprises — en dedupliquant
     * les textes identiques (les quetes converties du legacy dupliquent le chapitre).
     */
    private void chapterFields(StringBuilder b, ChapterJpaEntity ch, QuestJpaEntity fused, Structure st) {
        illustrations(b, ch.getIllustrationImageIds());
        if (fused == null) {
            block(b, "Description", ch.getDescription());
            block(b, "Objectifs joueurs", ch.getPlayerObjectives());
            block(b, "Enjeux narratifs", ch.getNarrativeStakes());
            box(b, "secret", "Notes MJ", ch.getGmNotes());
            return;
        }
        illustrations(b, fused.getIllustrationImageIds());
        block(b, "Description", mergeField(ch.getDescription(), fused.getDescription()));
        block(b, "Conditions de déblocage", renderPrerequisites(fused.getPrerequisites(), st.questNames));
        block(b, "Nœuds liés", renderQuestNodes(fused, st));
        block(b, "Objectifs joueurs", mergeField(ch.getPlayerObjectives(), fused.getPlayerObjectives()));
        block(b, "Enjeux narratifs", mergeField(ch.getNarrativeStakes(), fused.getNarrativeStakes()));
        box(b, "secret", "Notes MJ", mergeField(ch.getGmNotes(), fused.getGmNotes()));
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
        ctx.toc(0, "Scènes", "part-narrative");

        illustrations(b, arc.getIllustrationImageIds());
        block(b, "Description", arc.getDescription());
        block(b, "Themes", arc.getThemes());
        block(b, "Enjeux", arc.getStakes());
        block(b, "Recompenses", arc.getRewards());
        block(b, "Resolution", arc.getResolution());
        box(b, "secret", "Notes MJ", arc.getGmNotes());
        chapterFields(b, ch, st.questByContainerChapter.get(ch.getId()), st);

        StringBuilder scBms = new StringBuilder();
        for (SceneJpaEntity sc : st.scenesByChapter.get(ch.getId())) {
            ctx.toc(1, sc.getName(), "scene-" + sc.getId());
            scBms.append(bookmark(sc.getName(), "scene-" + sc.getId(), ""));
            scene(b, sc, st);
            ctx.scenes++;
        }
        ctx.bookmarks.append(bookmark("Scènes", "part-narrative", scBms.toString()));
    }

    private void scene(StringBuilder b, SceneJpaEntity sc, Structure st) {
        b.append("<div class=\"scene\" id=\"scene-").append(sc.getId()).append("\"><div class=\"scene-head\">")
                .append("<span class=\"eyebrow\">Scène</span>")
                .append(esc(sc.getName())).append("</div>");
        // Contexte : lieu et moment en une ligne italique sous le titre (comme le
        // sous-titre d'une rencontre dans un livre de JdR) ; repli en champs normaux
        // si le texte est long/multiligne.
        if (isMetaShort(sc.getLocation()) && isMetaShort(sc.getTiming())
                && (notBlank(sc.getLocation()) || notBlank(sc.getTiming()))) {
            List<String> meta = new ArrayList<>();
            if (notBlank(sc.getLocation())) meta.add(sc.getLocation().trim());
            if (notBlank(sc.getTiming())) meta.add(sc.getTiming().trim());
            b.append("<div class=\"scene-meta\">").append(esc(String.join("  —  ", meta))).append("</div>");
        } else {
            block(b, "Lieu", sc.getLocation());
            block(b, "Moment", sc.getTiming());
        }
        block(b, "Ambiance", sc.getAtmosphere(), "ambiance");
        box(b, "readaloud", "À lire aux joueurs", sc.getPlayerNarration());
        box(b, "secret", "Notes secrètes MJ", sc.getGmSecretNotes());
        block(b, "Choix & consequences", sc.getChoicesConsequences());
        block(b, "Sorties", renderSceneBranches(sc.getBranches(), st));
        // Combat : difficulte + ennemis de la rencontre (refs bestiaire resolues en noms
        // + texte libre) dans le meme encadre rouge.
        box(b, "combat", "Combat", joinNonBlank(sc.getCombatDifficulty(),
                enemyLines(sc.getEnemyIds(), sc.getEnemies(), st)));
        illustrations(b, sc.getIllustrationImageIds());
        // Battlemaps (images uniquement ; les videos ne sont pas rendables en PDF).
        // Le libellé de la variante (Jour/Nuit…) est rendu en légende sous l'image.
        if (sc.getBattlemaps() != null) {
            for (var bm : sc.getBattlemaps()) {
                PdfImage battlemap = fileImage(bm.mediaFileId());
                if (battlemap == null) continue;
                String caption = bm.label() == null || bm.label().isBlank()
                        ? "Battlemap" : "Battlemap — " + bm.label();
                illustration(b, battlemap, caption);
            }
        }
        rooms(b, sc, st);
        b.append("</div>");
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
            b.append("</span>").append(esc(r.getName())).append("</div>");
            block(b, "Description", r.getDescription());
            box(b, "combat", "Ennemis", enemyLines(r.getEnemyIds(), r.getEnemies(), st));
            block(b, "Butin", r.getLoot());
            block(b, "Pièges", r.getTraps());
            box(b, "secret", "Notes MJ", r.getGmNotes());
            PdfImage map = image(r.getMapImageId(), ILLUSTRATION_MAX);
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
            b.append("</div>");
        }
    }

    /** Sorties narratives d'une scene, cibles resolues en noms de scenes. */
    private String renderSceneBranches(List<SceneBranch> branches, Structure st) {
        if (branches == null || branches.isEmpty()) return null;
        List<String> parts = new ArrayList<>();
        for (SceneBranch br : branches) {
            String kind = br.kind() == LinkType.CLUE ? "indice"
                    : br.kind() == LinkType.LEAD ? "piste" : null;
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
            if (out.length() > 0) out.append("\n\n");
            out.append(p);
        }
        return out.length() == 0 ? null : out.toString();
    }

    // ----- Quetes libres / transversales (celles SANS conteneur dans la narration) -----

    private void quests(Ctx ctx, Structure st) {
        List<QuestJpaEntity> quests = st.standaloneQuests;
        if (quests.isEmpty()) return;

        StringBuilder b = ctx.body;
        b.append("<h1 class=\"part\" id=\"part-quests\">Quêtes</h1>");
        ctx.toc(0, "Quêtes", "part-quests");
        StringBuilder qBms = new StringBuilder();
        for (QuestJpaEntity q : quests) qBms.append(bookmark(q.getName(), "quest-" + q.getId(), ""));
        ctx.bookmarks.append(bookmark("Quêtes", "part-quests", qBms.toString()));

        for (QuestJpaEntity q : quests) {
            ctx.toc(1, q.getName(), "quest-" + q.getId());
            b.append("<div class=\"chapter\"><div class=\"chapter-head\" id=\"quest-").append(q.getId())
                    .append("\"><span class=\"eyebrow\">Quête</span>").append(esc(q.getName())).append("</div>");
            illustrations(b, q.getIllustrationImageIds());
            block(b, "Description", q.getDescription());
            block(b, "Conditions de déblocage", renderPrerequisites(q.getPrerequisites(), st.questNames));
            block(b, "Nœuds liés", renderQuestNodes(q, st));
            block(b, "Objectifs joueurs", q.getPlayerObjectives());
            block(b, "Enjeux narratifs", q.getNarrativeStakes());
            box(b, "secret", "Notes MJ", q.getGmNotes());
            // Scenes de ses conteneurs (arc SYSTEM, masque de la narration) : c'est ICI
            // que vit le contenu jouable d'une quete libre.
            Set<String> containers = st.containerChapterIds(q);
            if (q.getNodes() != null) {
                for (QuestNodeRef n : q.getNodes()) {
                    if (n.nodeType() != NodeType.CHAPTER || !containers.contains(n.nodeId())) continue;
                    List<SceneJpaEntity> scenes = st.scenesByChapter.get(Long.parseLong(n.nodeId()));
                    if (scenes == null) continue;
                    for (SceneJpaEntity sc : scenes) {
                        scene(b, sc, st);
                        ctx.scenes++;
                    }
                }
            }
            b.append("</div>");
        }
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
            if (s.length() > 0) s.append('\n');
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
        ctx.toc(0, title, partId);

        // Groupement par dossier (dossiers tries, non-classes en dernier).
        Map<String, List<PersonaRow>> byFolder = new TreeMap<>();
        List<PersonaRow> ungrouped = new ArrayList<>();
        for (PersonaRow r : rows) {
            String f = r.folder() != null ? r.folder().trim() : "";
            if (f.isEmpty()) ungrouped.add(r);
            else byFolder.computeIfAbsent(f, k -> new ArrayList<>()).add(r);
        }
        StringBuilder fBms = new StringBuilder();
        int fi = 0;
        for (Map.Entry<String, List<PersonaRow>> e : byFolder.entrySet()) {
            String anchor = partId + "-f" + (fi++);
            String label = e.getKey().replace("/", " / ");
            ctx.toc(1, label, anchor);
            fBms.append(bookmark(label, anchor, ""));
            b.append("<h3 class=\"folder\" id=\"").append(anchor).append("\">").append(esc(label)).append("</h3>");
            e.getValue().sort(java.util.Comparator.comparingInt(PersonaRow::order));
            for (PersonaRow r : e.getValue()) personaCard(b, r, template, enemy);
        }
        if (!ungrouped.isEmpty()) {
            if (!byFolder.isEmpty()) {
                String anchor = partId + "-f" + fi;
                ctx.toc(1, "Sans dossier", anchor);
                fBms.append(bookmark("Sans dossier", anchor, ""));
                b.append("<h3 class=\"folder\" id=\"").append(anchor).append("\">Sans dossier</h3>");
            }
            ungrouped.sort(java.util.Comparator.comparingInt(PersonaRow::order));
            for (PersonaRow r : ungrouped) personaCard(b, r, template, enemy);
        }
        ctx.bookmarks.append(bookmark(title, partId, fBms.toString()));
    }

    private void personaCard(StringBuilder b, PersonaRow r, List<TemplateField> template, boolean enemy) {
        // Mise en page en TABLE (portrait | contenu) : openhtmltopdf gere mal le float
        // + overflow (le texte se superposait au portrait).
        b.append("<div class=\"card\"><table class=\"persona\"><tr>");
        PdfImage portrait = image(r.portraitId(), PORTRAIT_MAX);
        if (portrait != null) {
            double widthCm = Math.min(3.0, PORTRAIT_MAX_HEIGHT_CM * portrait.w() / (double) portrait.h());
            b.append("<td class=\"persona-portrait\"><img style=\"width:").append(cm(widthCm))
                    .append("\" src=\"").append(portrait.uri()).append("\"/></td>");
        }
        b.append("<td class=\"persona-content\">");
        b.append("<div class=\"persona-name\">").append(esc(r.name()));
        if (notBlank(r.level())) b.append(" <span class=\"level\">Niv. ").append(esc(r.level())).append("</span>");
        b.append("</div>");

        renderFields(b, template, r.values(), r.keyValueValues(), r.imageValues(), null);

        if (enemy && r.foundryStats() != null) {
            Map<String, String> clean = cleanStats(r.foundryStats());
            if (!clean.isEmpty()) {
                b.append("<div class=\"field-label\">Statistiques</div><table class=\"stats-table\"><tbody>");
                int row = 0;
                for (Map.Entry<String, String> s : clean.entrySet()) {
                    b.append(row++ % 2 == 1 ? "<tr class=\"alt\">" : "<tr>")
                            .append("<th>").append(esc(s.getKey())).append("</th><td>")
                            .append(esc(s.getValue())).append("</td></tr>");
                }
                b.append("</tbody></table>");
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
            String key = e.getKey();
            String val = e.getValue();
            if (key == null || val == null) continue;
            String v = val.trim();
            if (STAT_NOISE_VALUES.contains(v.toLowerCase())) continue;
            String lk = key.toLowerCase();
            if (lk.contains("rollmode") || lk.endsWith(".defaultrollmode")) continue;
            out.put(humanizeStatKey(key), v);
        }
        return out;
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
        ctx.toc(0, "Tables aléatoires", "part-tables");
        StringBuilder tBms = new StringBuilder();
        for (RandomTableJpaEntity t : tables) tBms.append(bookmark(t.getName(), "rt-" + t.getId(), ""));
        ctx.bookmarks.append(bookmark("Tables aléatoires", "part-tables", tBms.toString()));

        for (RandomTableJpaEntity t : tables) {
            ctx.toc(1, t.getName(), "rt-" + t.getId());
            b.append("<div class=\"card\" id=\"rt-").append(t.getId()).append("\"><div class=\"card-body\">");
            b.append("<div class=\"persona-name\">").append(esc(t.getName()));
            if (notBlank(t.getDiceFormula())) {
                b.append(" <span class=\"level\">").append(esc(t.getDiceFormula())).append("</span>");
            }
            b.append("</div>");
            block(b, "Description", t.getDescription());
            if (!t.getEntries().isEmpty()) {
                b.append("<table class=\"stats-table\"><thead><tr><th class=\"roll-col\">")
                        .append(esc(notBlank(t.getDiceFormula()) ? t.getDiceFormula() : "Jet"))
                        .append("</th><th>Résultat</th></tr></thead><tbody>");
                int row = 0;
                for (RandomTableEntryJpaEntity e : t.getEntries()) {
                    String roll = e.getMinRoll() == e.getMaxRoll()
                            ? String.valueOf(e.getMinRoll())
                            : e.getMinRoll() + "–" + e.getMaxRoll();
                    b.append(row++ % 2 == 1 ? "<tr class=\"alt\">" : "<tr>")
                            .append("<td class=\"roll-col\">").append(esc(roll)).append("</td><td>")
                            .append(esc(e.getLabel()));
                    if (notBlank(e.getDetail())) {
                        b.append("<br/><span class=\"entry-detail\">").append(multiline(e.getDetail())).append("</span>");
                    }
                    b.append("</td></tr>");
                }
                b.append("</tbody></table>");
            }
            b.append("</div></div>");
        }
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
        ctx.toc(0, "Lore", "part-lore");

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
            String label = e.getKey().isEmpty() ? "Sans dossier" : e.getKey();
            String anchor = "lore-f" + (fi++);
            ctx.toc(1, label, anchor);
            fBms.append(bookmark(label, anchor, ""));
            b.append("<h3 class=\"folder\" id=\"").append(anchor).append("\">").append(esc(label)).append("</h3>");
            e.getValue().sort(java.util.Comparator.comparingInt(PageJpaEntity::getOrder));
            for (PageJpaEntity p : e.getValue()) {
                b.append("<div class=\"card\"><div class=\"card-body\"><div class=\"persona-name\">")
                        .append(esc(p.getTitle())).append("</div>");
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
            FieldType type = f.getType();
            switch (type) {
                case TEXT, NUMBER -> block(b, f.getName(), values != null ? values.get(f.getName()) : null);
                case KEY_VALUE_LIST -> {
                    Map<String, String> inner = keyValueValues != null ? keyValueValues.get(f.getName()) : null;
                    List<String> labels = f.getLabels();
                    if (inner != null && labels != null && labels.stream().anyMatch(l -> notBlank(inner.get(l)))) {
                        keyValueTable(b, f.getName(), labels, inner);
                    }
                }
                case IMAGE -> {
                    List<String> ids = imageValues != null ? imageValues.get(f.getName()) : null;
                    illustrations(b, ids);
                }
                case TABLE -> {
                    List<Map<String, String>> data = tableValues != null ? tableValues.get(f.getName()) : null;
                    List<String> cols = f.getLabels();
                    if (data != null && !data.isEmpty() && cols != null && !cols.isEmpty()) {
                        b.append("<div class=\"field-label\">").append(esc(f.getName()))
                                .append("</div><table class=\"stats-table\"><thead><tr>");
                        for (String col : cols) b.append("<th>").append(esc(col)).append("</th>");
                        b.append("</tr></thead><tbody>");
                        int row = 0;
                        for (Map<String, String> line : data) {
                            b.append(row++ % 2 == 1 ? "<tr class=\"alt\">" : "<tr>");
                            for (String col : cols) b.append("<td>").append(esc(line.get(col))).append("</td>");
                            b.append("</tr>");
                        }
                        b.append("</tbody></table>");
                    }
                }
            }
        }
    }

    /**
     * Liste cle/valeur : en TABLEAU HORIZONTAL compact quand cela ressemble a une ligne de
     * caracteristiques (peu d'entrees, libelles et valeurs courts — FOR/DEX/CON...),
     * sinon en tableau vertical classique a deux colonnes.
     */
    private void keyValueTable(StringBuilder b, String name, List<String> labels, Map<String, String> inner) {
        boolean compact = labels.size() >= 2 && labels.size() <= 8
                && labels.stream().allMatch(l -> l != null && l.length() <= 5)
                && labels.stream().allMatch(l -> {
                    String v = inner.get(l);
                    return v == null || v.trim().length() <= 8;
                });
        b.append("<div class=\"field-label\">").append(esc(name)).append("</div>");
        if (compact) {
            b.append("<table class=\"stat-array\"><tr>");
            for (String label : labels) b.append("<th>").append(esc(label)).append("</th>");
            b.append("</tr><tr>");
            for (String label : labels) {
                String v = inner.get(label);
                b.append("<td>").append(notBlank(v) ? esc(v) : "—").append("</td>");
            }
            b.append("</tr></table>");
        } else {
            b.append("<table class=\"stats-table\"><tbody>");
            int row = 0;
            for (String label : labels) {
                String v = inner.get(label);
                if (!notBlank(v)) continue;
                b.append(row++ % 2 == 1 ? "<tr class=\"alt\">" : "<tr>")
                        .append("<th>").append(esc(label)).append("</th><td>")
                        .append(esc(v)).append("</td></tr>");
            }
            b.append("</tbody></table>");
        }
    }

    // ----- Bloc "libelle + texte" -----

    /** Un paragraphe (lignes jointes par &lt;br/&gt;) ou une liste a puces. */
    private record TextBlock(boolean list, List<String> lines) {}

    /** Un lieu/moment assez court pour la ligne de contexte de scene (sinon champ normal). */
    private static boolean isMetaShort(String s) {
        return s == null || (!s.contains("\n") && !s.contains("\r") && s.trim().length() <= 90);
    }

    /**
     * ENCADRE special (codes visuels des livres de JdR) : "readaloud" = texte a lire aux
     * joueurs (parchemin, filets or), "secret" = reserve au MJ (violet tirete), "combat" =
     * rencontre (accent rouge). Libelle sur sa propre ligne, contenu en blocs.
     */
    private void box(StringBuilder b, String cssClass, String label, String value) {
        if (!notBlank(value)) return;
        b.append("<div class=\"box ").append(cssClass).append("\">");
        b.append("<div class=\"box-label\">").append(esc(label)).append("</div>");
        List<TextBlock> blocks = parseBlocks(value);
        for (int i = 0; i < blocks.size(); i++) {
            TextBlock t = blocks.get(i);
            if (t.list()) {
                b.append("<ul>");
                for (String line : t.lines()) b.append("<li>").append(esc(line)).append("</li>");
                b.append("</ul>");
            } else if (i == 0) {
                b.append(paragraphHtml(t));
            } else {
                b.append("<div class=\"para\">").append(paragraphHtml(t)).append("</div>");
            }
        }
        b.append("</div>");
    }

    /**
     * Bloc "libelle en tete de ligne + valeur" si la valeur est non vide. Le libelle est
     * rendu EN LIGNE devant le premier paragraphe (style stat-block : compact et balayable),
     * les paragraphes suivants et les listes a puces (lignes "- ...") en dessous.
     */
    private void block(StringBuilder b, String label, String value) {
        block(b, label, value, null);
    }

    /** Variante avec classe CSS additionnelle sur le champ (ex : "ambiance" -> italique). */
    private void block(StringBuilder b, String label, String value, String extraClass) {
        if (!notBlank(value)) return;
        List<TextBlock> blocks = parseBlocks(value);
        b.append("<div class=\"field").append(extraClass != null ? " " + extraClass : "")
                .append("\"><span class=\"field-label\">").append(esc(label)).append("</span>");
        int i = 0;
        if (!blocks.isEmpty() && !blocks.get(0).list()) {
            b.append(paragraphHtml(blocks.get(0)));
            i = 1;
        }
        for (; i < blocks.size(); i++) {
            TextBlock t = blocks.get(i);
            if (t.list()) {
                b.append("<ul>");
                for (String line : t.lines()) b.append("<li>").append(esc(line)).append("</li>");
                b.append("</ul>");
            } else {
                b.append("<div class=\"para\">").append(paragraphHtml(t)).append("</div>");
            }
        }
        b.append("</div>");
    }

    private static String paragraphHtml(TextBlock t) {
        StringBuilder p = new StringBuilder();
        for (int i = 0; i < t.lines().size(); i++) {
            if (i > 0) p.append("<br/>");
            p.append(esc(t.lines().get(i)));
        }
        return p.toString();
    }

    /**
     * Decoupe un texte brut en blocs : les lignes vides separent les paragraphes, les
     * lignes commencant par "- ", "• " ou "* " deviennent de vraies listes a puces.
     */
    private static List<TextBlock> parseBlocks(String value) {
        String norm = value.replace("\r\n", "\n").replace('\r', '\n');
        List<TextBlock> blocks = new ArrayList<>();
        List<String> cur = new ArrayList<>();
        boolean curList = false;
        for (String line : norm.split("\n", -1)) {
            String t = line.trim();
            if (t.isEmpty()) {
                if (!cur.isEmpty()) blocks.add(new TextBlock(curList, cur));
                cur = new ArrayList<>();
                continue;
            }
            boolean bullet = t.startsWith("- ") || t.startsWith("• ") || t.startsWith("* ");
            if (!cur.isEmpty() && bullet != curList) {
                blocks.add(new TextBlock(curList, cur));
                cur = new ArrayList<>();
            }
            curList = bullet;
            cur.add(bullet ? t.substring(2).trim() : t);
        }
        if (!cur.isEmpty()) blocks.add(new TextBlock(curList, cur));
        return blocks;
    }

    // ----- Illustrations -----

    /** Galerie d'illustrations (liste d'ids d'images) -> blocs image centres, hauteur bornee. */
    private void illustrations(StringBuilder b, List<String> imageIds) {
        if (imageIds == null) return;
        for (String id : imageIds) {
            PdfImage img = image(id, ILLUSTRATION_MAX);
            if (img != null) illustration(b, img, null);
        }
    }

    /**
     * Une illustration centree, avec legende optionnelle. La largeur est calculee pour que
     * la hauteur affichee reste bornee : une image tres verticale ne mange plus la page.
     */
    private void illustration(StringBuilder b, PdfImage img, String caption) {
        double widthCm = Math.min(CONTENT_WIDTH_CM,
                ILLUSTRATION_MAX_HEIGHT_CM * img.w() / (double) img.h());
        b.append("<div class=\"illus\"><img style=\"width:").append(cm(widthCm))
                .append("\" src=\"").append(img.uri()).append("\"/>");
        if (caption != null) {
            b.append("<div class=\"caption\">").append(esc(caption)).append("</div>");
        }
        b.append("</div>");
    }

    private static String cm(double v) {
        return String.format(Locale.ROOT, "%.1fcm", v);
    }

    // ====================================================================== Images

    /** Image re-encodee prete a inliner : data-URI + dimensions reelles (pour l'aspect). */
    private record PdfImage(String uri, int w, int h) {}

    /** Image LoreMind re-encodee (data-URI JPEG redimensionne), ou null. */
    private PdfImage image(String imageId, int maxDim) {
        if (imageId == null || imageId.isBlank()) return null;
        ImageJpaEntity e;
        try {
            e = imageRepo.findById(Long.parseLong(imageId)).orElse(null);
        } catch (NumberFormatException ex) {
            return null;
        }
        if (e == null) return null;
        return encode(imageStorage.download(e.getStorageKey()), maxDim, imageId);
    }

    /** Battlemap (fichier stocke) re-encodee, seulement si c'est une image (pas une video). */
    private PdfImage fileImage(String fileId) {
        if (fileId == null || fileId.isBlank()) return null;
        StoredFileJpaEntity e;
        try {
            e = storedFileRepo.findById(Long.parseLong(fileId)).orElse(null);
        } catch (NumberFormatException ex) {
            return null;
        }
        if (e == null) return null;
        String ct = e.getContentType();
        if (ct == null || !ct.startsWith("image/")) return null; // mp4/webm -> ignore
        return encode(fileStorage.download(e.getStorageKey()), ILLUSTRATION_MAX, fileId);
    }

    /** Lit un flux image, le redimensionne (max maxDim) et le re-encode en data-URI JPEG. */
    private PdfImage encode(InputStream in, int maxDim, String ref) {
        if (in == null) return null;
        try (in) {
            BufferedImage src = ImageIO.read(in);
            if (src == null) {
                log.debug("Image PDF ignoree (format non decode) : {}", ref);
                return null;
            }
            int w = src.getWidth(), h = src.getHeight();
            double scale = Math.min(1.0, (double) maxDim / Math.max(w, h));
            int nw = Math.max(1, (int) Math.round(w * scale));
            int nh = Math.max(1, (int) Math.round(h * scale));
            BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = dst.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(Color.WHITE); // fond blanc : aplatit la transparence (JPEG sans alpha)
            g.fillRect(0, 0, nw, nh);
            g.drawImage(src, 0, 0, nw, nh, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(dst, "jpeg", out);
            return new PdfImage("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(out.toByteArray()),
                    nw, nh);
        } catch (IOException | RuntimeException ex) {
            log.warn("Image PDF ignoree ({}) : {}", ref, ex.getMessage());
            return null;
        }
    }

    // ====================================================================== Helpers

    private List<TemplateField> resolveTemplate(String gameSystemId, boolean npc) {
        if (gameSystemId == null || gameSystemId.isBlank()) return null;
        try {
            return gameSystemRepo.findById(Long.parseLong(gameSystemId))
                    .map(gs -> npc ? gs.getNpcTemplate() : gs.getEnemyTemplate())
                    .orElse(null);
        } catch (NumberFormatException ex) {
            return null;
        }
    }

    private static List<TemplateField> fieldsOf(TemplateJpaEntity t) {
        return t != null ? t.getFields() : null;
    }

    /** Tri par ORDRE manuel (glisser-déposer) — cohérent avec l'arbre et les cartes. */
    private static <T> List<T> sortByOrder(List<T> list, java.util.function.ToIntFunction<T> order) {
        List<T> copy = new ArrayList<>(list);
        copy.sort(java.util.Comparator.comparingInt(order));
        return copy;
    }

    private static boolean notBlank(String s) {
        return s != null && !s.isBlank();
    }

    /** Un signet PDF (childrenHtml : signets enfants deja rendus, ou chaine vide). */
    private static String bookmark(String name, String anchor, String childrenHtml) {
        return "<bookmark name=\"" + esc(name == null ? "" : name) + "\" href=\"#" + anchor + "\">"
                + childrenHtml + "</bookmark>";
    }

    /**
     * Echappe le texte pour XHTML et retire les caracteres interdits en XML 1.0.
     * Les glyphes exotiques (fleches, ≈, cyrillique...) sont couverts par les polices
     * DejaVu embarquees ; seuls les emojis (hors plan de base, absents de DejaVu)
     * sont retires pour ne pas sortir en "#".
     */
    private static String esc(String s) {
        if (s == null) return "";
        StringBuilder b = new StringBuilder(s.length() + 16);
        for (int i = 0; i < s.length(); i++) {
            char c = s.charAt(i);
            if (c < 0x20 && c != '\n' && c != '\t') continue; // \r normalise en amont
            switch (c) {
                case '&' -> b.append("&amp;");
                case '<' -> b.append("&lt;");
                case '>' -> b.append("&gt;");
                case '"' -> b.append("&quot;");
                case '\u202F', '\u2007' -> b.append('\u00A0'); // espaces fines (U+202F, U+2007) -> insecable
                default -> {
                    if (!Character.isSurrogate(c)) b.append(c); // emojis : sans glyphe, on retire
                }
            }
        }
        return b.toString();
    }

    /** Comme esc, mais les sauts de ligne deviennent des &lt;br/&gt;. */
    private static String multiline(String s) {
        if (s == null) return "";
        return esc(s.replace("\r\n", "\n").replace('\r', '\n')).replace("\n", "<br/>");
    }

    /** CSS final : en-tete courant renseigne avec le nom de la campagne. */
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
    private static final String CSS = """
        @page { size: A4; margin: 2.2cm 1.7cm 2cm;
          @top-center { content: '__HEADER__'; font-family: 'DejaVu Sans', sans-serif; font-size: 7.5pt;
            letter-spacing: .22em; text-transform: uppercase; color: #b3a9d6; }
          @bottom-center { content: counter(page); font-family: 'DejaVu Sans', sans-serif; font-size: 9pt; color: #999; } }
        @page:first { @top-center { content: none; } }
        body { font-family: 'DejaVu Serif', serif; font-size: 10pt; color: #26243a; line-height: 1.5; }
        a { color: inherit; text-decoration: none; }
        /* --- Couverture --- */
        .cover { text-align: center; padding-top: 5.5cm; page-break-after: always; }
        .cover .subtitle { font-family: 'DejaVu Sans', sans-serif; font-size: 11pt; letter-spacing: .35em;
          text-transform: uppercase; color: #8a7bc8; }
        .cover-title { font-family: 'DejaVu Sans', sans-serif; font-size: 30pt; text-transform: uppercase;
          letter-spacing: .04em; color: #2e2a4a; margin: .45cm 0 0; }
        .cover-rule { width: 3.6cm; border-bottom: 2.5pt solid #8a7bc8; margin: .5cm auto; }
        .cover-meta { font-family: 'DejaVu Sans', sans-serif; font-size: 9.5pt; color: #8076a3; letter-spacing: .06em; }
        .cover-desc { margin: 1.1cm auto 0; max-width: 12.5cm; color: #444; }
        .cover-date { margin-top: 1.4cm; font-family: 'DejaVu Sans', sans-serif; font-size: 8.5pt; color: #b0aac4; }
        /* --- Sommaire --- */
        .toc-page { page-break-after: always; }
        h1.toc-title { font-family: 'DejaVu Sans', sans-serif; font-size: 20pt; text-transform: uppercase;
          letter-spacing: .06em; color: #2e2a4a; border-bottom: 2pt solid #8a7bc8;
          padding-bottom: 4pt; margin: 0 0 .55cm; }
        table.toc { width: 100%; border-collapse: collapse; font-family: 'DejaVu Sans', sans-serif; }
        .toc td { border-bottom: 1pt dotted #d8d2e8; padding: 3pt 0 2pt; vertical-align: bottom; }
        .toc td.p { width: 1.2cm; text-align: right; font-size: 9.5pt; color: #666; }
        .toc td.p a:after { content: target-counter(attr(href), page); }
        .toc tr.lvl0 td { font-weight: bold; font-size: 11pt; color: #2e2a4a; padding-top: 9pt;
          border-bottom: 1pt solid #b9aede; }
        .toc tr.lvl1 td.t { padding-left: .55cm; font-size: 10pt; }
        .toc tr.lvl2 td.t { padding-left: 1.1cm; font-size: 9pt; color: #555; }
        /* --- Parties et hierarchie narrative : Arc (bandeau) > Chapitre (teinte) > Scene (carte). --- */
        h1.part { page-break-before: always; page-break-after: avoid; font-family: 'DejaVu Sans', sans-serif;
          font-size: 20pt; text-transform: uppercase; letter-spacing: .06em; color: #2e2a4a;
          border-bottom: 2pt solid #8a7bc8; padding-bottom: 4pt; margin: 0 0 .55cm; }
        h3.folder { page-break-after: avoid; font-family: 'DejaVu Sans', sans-serif; color: #8a7bc8;
          text-transform: uppercase; letter-spacing: .05em; font-size: 10.5pt;
          border-bottom: 1pt dotted #ccc; padding-bottom: 2pt; margin: .9em 0 .3em; }
        .arc { margin: .4cm 0 .3cm; }
        .arc-head { page-break-after: avoid; page-break-inside: avoid; font-family: 'DejaVu Sans', sans-serif; background: #2e2a4a; color: #fff;
          font-size: 14pt; font-weight: bold; padding: .22cm .4cm; border-radius: 4pt; margin: 0 0 .35cm; }
        .chapter { margin: .5cm 0 .35cm; }
        .chapter-head { page-break-after: avoid; page-break-inside: avoid; font-family: 'DejaVu Sans', sans-serif; background: #f1eef9;
          border-left: 5pt solid #8a7bc8; padding: .16cm .4cm; font-size: 12.5pt; font-weight: bold;
          color: #463b78; margin: 0 0 .25cm; }
        .scene { margin: .3cm 0 .35cm .35cm; border: 1pt solid #e6e6ee; border-left: 3pt solid #9bb06a;
          border-radius: 4pt; padding: .25cm .4cm; background: #fbfbfd; }
        .scene-head { page-break-after: avoid; page-break-inside: avoid; font-family: 'DejaVu Sans', sans-serif; font-size: 11.5pt;
          font-weight: bold; color: #5a6e3a; margin-bottom: .12cm; }
        .room { margin: .22cm 0 .22cm .3cm; border: 1pt solid #ece7dc; border-left: 3pt solid #c9a86a;
          border-radius: 4pt; padding: .2cm .35cm; background: #fdfcf8; }
        .room-head { page-break-after: avoid; page-break-inside: avoid; font-family: 'DejaVu Sans', sans-serif;
          font-size: 10.5pt; font-weight: bold; color: #7a5f33; margin-bottom: .08cm; }
        .eyebrow { display: block; font-family: 'DejaVu Sans', sans-serif; font-size: 6.5pt; text-transform: uppercase;
          letter-spacing: .16em; font-weight: normal; color: #9182bd; }
        .eyebrow-light { color: #c9c0e8; }
        .scene-meta { font-style: italic; color: #6d6787; margin: -.05cm 0 .16cm; }
        /* --- Encadres speciaux (codes visuels des livres de JdR) --- */
        .box { border-radius: 3pt; padding: .2cm .35cm; margin: .2cm 0; page-break-inside: avoid; }
        .box-label { font-family: 'DejaVu Sans', sans-serif; font-size: 7.5pt; text-transform: uppercase;
          letter-spacing: .1em; font-weight: bold; font-style: normal; margin-bottom: .06cm; }
        .readaloud { background: #faf6ea; border-top: 2pt solid #cbbd93; border-bottom: 2pt solid #cbbd93;
          border-radius: 0; font-style: italic; }
        .readaloud .box-label { color: #a08b4f; }
        .secret { background: #f5f2fa; border: 1pt dashed #ab9fd6; }
        .secret .box-label { color: #7d6fb0; }
        .combat { background: #fdf5f4; border-left: 3pt solid #c0605a; }
        .combat .box-label { color: #a84f49; }
        .field.ambiance { font-style: italic; }
        .field.ambiance .field-label { font-style: normal; }
        /* --- Champs : libelle en tete de ligne, texte a la suite (style stat-block). --- */
        .field { margin: .16cm 0; }
        .field-label { font-family: 'DejaVu Sans', sans-serif; font-size: 7.5pt; text-transform: uppercase;
          letter-spacing: .08em; color: #7d6fb0; font-weight: bold; padding-right: .18cm; }
        div.field-label { margin: .12cm 0 .06cm; padding-right: 0; }
        .para { margin: .1cm 0 0; }
        .field ul { margin: .08cm 0; padding-left: .55cm; }
        .field li { margin: 0 0 .05cm; }
        /* --- Cartes (fiches PNJ/ennemis, pages de lore) --- */
        .card { page-break-inside: avoid; border: 1pt solid #e3e0ee; border-radius: 4pt;
          padding: .32cm .4cm; margin: .32cm 0; background: #fcfcfe; }
        .card-body { display: block; }
        .persona { width: 100%; border-collapse: collapse; }
        .persona-portrait { width: 3cm; vertical-align: top; padding: 0 .45cm 0 0; }
        .persona-portrait img { border: 1pt solid #ccc; border-radius: 3pt; }
        .persona-content { vertical-align: top; }
        .persona-name { font-family: 'DejaVu Sans', sans-serif; font-size: 12pt; font-weight: bold; color: #2e2a4a;
          margin-bottom: .1cm; }
        .level { font-size: 9pt; color: #8a7bc8; font-weight: normal; }
        /* --- Illustrations et battlemaps --- */
        .illus { margin: .3cm 0; text-align: center; page-break-inside: avoid; }
        .illus img { border: 1pt solid #cfc9e0; border-radius: 3pt; }
        .illus .caption { font-family: 'DejaVu Sans', sans-serif; font-size: 8pt; text-transform: uppercase;
          letter-spacing: .12em; color: #8076a3; margin-top: .1cm; }
        /* --- Tables --- */
        .stats-table { width: 100%; border-collapse: collapse; font-family: 'DejaVu Sans', sans-serif; font-size: 9pt;
          margin: .12cm 0 .28cm; }
        .stats-table th, .stats-table td { border-bottom: 1pt solid #e9e6f2; padding: 2.5pt 6pt;
          text-align: left; vertical-align: top; }
        .stats-table thead th { background: #f1eef9; color: #4a3f7a; border-bottom: 1pt solid #d8d1ec; }
        .stats-table tbody th { width: 32%; color: #555; font-weight: bold; background: #f8f7fc; }
        .stats-table tr.alt td { background: #f6f5fa; }
        .stats-table .roll-col { width: 1.8cm; text-align: center; font-weight: bold; color: #4a3f7a; }
        .stats-table .entry-detail { color: #555; font-size: 8.5pt; }
        .stat-array { border-collapse: collapse; font-family: 'DejaVu Sans', sans-serif; font-size: 9pt;
          margin: .12cm 0 .28cm; }
        .stat-array th { background: #f1eef9; color: #4a3f7a; border: 1pt solid #ddd6ee;
          padding: 2.5pt 9pt; text-align: center; }
        .stat-array td { border: 1pt solid #e6e2f2; padding: 2.5pt 9pt; text-align: center; }
        """;
}
