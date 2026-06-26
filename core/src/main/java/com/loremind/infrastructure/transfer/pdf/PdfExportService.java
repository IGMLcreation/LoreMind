package com.loremind.infrastructure.transfer.pdf;

import com.loremind.domain.files.ports.FileStorage;
import com.loremind.domain.images.ports.ImageStorage;
import com.loremind.domain.shared.template.FieldType;
import com.loremind.domain.shared.template.TemplateField;
import com.loremind.infrastructure.persistence.entity.*;
import com.loremind.infrastructure.persistence.jpa.*;
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
import java.util.*;

/**
 * Export d'UNE campagne en livret PDF (joli document imprimable) : structure narrative
 * (arcs/quetes/scenes), PNJ &amp; ennemis (bestiaire), lore, et battlemaps en illustration.
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

    private final CampaignJpaRepository campaignRepo;
    private final ArcJpaRepository arcRepo;
    private final ChapterJpaRepository chapterRepo;
    private final SceneJpaRepository sceneRepo;
    private final NpcJpaRepository npcRepo;
    private final EnemyJpaRepository enemyRepo;
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
                            NpcJpaRepository npcRepo, EnemyJpaRepository enemyRepo,
                            GameSystemJpaRepository gameSystemRepo, ImageJpaRepository imageRepo,
                            StoredFileJpaRepository storedFileRepo,
                            LoreNodeJpaRepository loreNodeRepo, PageJpaRepository pageRepo,
                            TemplateJpaRepository templateRepo, ImageStorage imageStorage,
                            FileStorage fileStorage) {
        this.campaignRepo = campaignRepo;
        this.arcRepo = arcRepo;
        this.chapterRepo = chapterRepo;
        this.sceneRepo = sceneRepo;
        this.npcRepo = npcRepo;
        this.enemyRepo = enemyRepo;
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

    // ====================================================================== XHTML

    private String buildXhtml(CampaignJpaEntity campaign) {
        List<TemplateField> npcTemplate = resolveTemplate(campaign.getGameSystemId(), true);
        List<TemplateField> enemyTemplate = resolveTemplate(campaign.getGameSystemId(), false);

        StringBuilder body = new StringBuilder();
        StringBuilder bookmarks = new StringBuilder();

        cover(body, campaign);
        narrative(body, bookmarks, campaign);
        personas(body, bookmarks, "part-npcs", "Personnages non-joueurs (PNJ)", npcEntries(campaign), npcTemplate, false);
        personas(body, bookmarks, "part-enemies", "Bestiaire", enemyEntries(campaign), enemyTemplate, true);
        lore(body, bookmarks, campaign);

        return "<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n"
                + "<html xmlns=\"http://www.w3.org/1999/xhtml\"><head><meta charset=\"utf-8\"/>\n"
                + "<style>" + CSS + "</style>\n"
                + "<bookmarks>" + bookmarks + "</bookmarks>\n"
                + "</head><body>" + body + "</body></html>";
    }

    private void cover(StringBuilder b, CampaignJpaEntity c) {
        b.append("<div class=\"cover\">");
        b.append("<div class=\"subtitle\">Livret de campagne</div>");
        b.append("<h1 class=\"cover-title\">").append(esc(c.getName())).append("</h1>");
        if (notBlank(c.getDescription())) {
            b.append("<div class=\"cover-desc\">").append(multiline(c.getDescription())).append("</div>");
        }
        b.append("</div>");
    }

    // ----- Structure narrative : arcs -> quetes -> scenes -----

    private void narrative(StringBuilder b, StringBuilder bm, CampaignJpaEntity campaign) {
        // Tri par ORDRE manuel (glisser-déposer) — cohérent avec l'arbre et les cartes.
        List<ArcJpaEntity> arcs = sortByOrder(arcRepo.findByCampaignId(campaign.getId()), ArcJpaEntity::getOrder);
        if (arcs.isEmpty()) return;

        b.append("<h1 class=\"part\" id=\"part-narrative\">Structure narrative</h1>");
        bm.append(bookmark("Structure narrative", "part-narrative", () -> {
            StringBuilder sub = new StringBuilder();
            for (ArcJpaEntity arc : arcs) sub.append(bookmark(arc.getName(), "arc-" + arc.getId(), null));
            return sub.toString();
        }));

        for (ArcJpaEntity arc : arcs) {
            b.append("<div class=\"arc\"><h2 class=\"arc-head\" id=\"arc-").append(arc.getId()).append("\">")
                    .append("<span class=\"eyebrow eyebrow-light\">Arc</span>")
                    .append(esc(arc.getName())).append("</h2>");
            illustrations(b, arc.getIllustrationImageIds());
            block(b, "Description", arc.getDescription());
            block(b, "Themes", arc.getThemes());
            block(b, "Enjeux", arc.getStakes());
            block(b, "Recompenses", arc.getRewards());
            block(b, "Resolution", arc.getResolution());
            block(b, "Notes MJ", arc.getGmNotes());

            for (ChapterJpaEntity ch : sortByOrder(chapterRepo.findByArcId(arc.getId()), ChapterJpaEntity::getOrder)) {
                b.append("<div class=\"quest\"><div class=\"quest-head\">")
                        .append("<span class=\"eyebrow\">Quête</span>")
                        .append(esc(ch.getName())).append("</div>");
                illustrations(b, ch.getIllustrationImageIds());
                block(b, "Description", ch.getDescription());
                block(b, "Objectifs joueurs", ch.getPlayerObjectives());
                block(b, "Enjeux narratifs", ch.getNarrativeStakes());
                block(b, "Notes MJ", ch.getGmNotes());

                for (SceneJpaEntity sc : sortByOrder(sceneRepo.findByChapterId(ch.getId()), SceneJpaEntity::getOrder)) {
                    scene(b, sc);
                }
                b.append("</div>");
            }
            b.append("</div>");
        }
    }

    private void scene(StringBuilder b, SceneJpaEntity sc) {
        b.append("<div class=\"scene\"><div class=\"scene-head\">")
                .append("<span class=\"eyebrow\">Scène</span>")
                .append(esc(sc.getName())).append("</div>");
        block(b, "Lieu", sc.getLocation());
        block(b, "Moment", sc.getTiming());
        block(b, "Ambiance", sc.getAtmosphere());
        block(b, "Narration joueur", sc.getPlayerNarration());
        block(b, "Notes secretes MJ", sc.getGmSecretNotes());
        block(b, "Choix & consequences", sc.getChoicesConsequences());
        block(b, "Difficulte du combat", sc.getCombatDifficulty());
        illustrations(b, sc.getIllustrationImageIds());
        // Battlemap (image uniquement ; les videos ne sont pas rendables en PDF).
        String battlemap = fileImageUri(sc.getBattlemapMediaFileId());
        if (battlemap != null) {
            b.append("<div class=\"illus\"><img src=\"").append(battlemap).append("\"/></div>");
        }
        b.append("</div>");
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

    private void personas(StringBuilder b, StringBuilder bm, String partId, String title,
                          List<PersonaRow> rows, List<TemplateField> template, boolean enemy) {
        if (rows.isEmpty()) return;
        b.append("<h1 class=\"part\" id=\"").append(partId).append("\">").append(esc(title)).append("</h1>");
        bm.append(bookmark(title, partId, null));

        // Groupement par dossier (dossiers tries, non-classes en dernier).
        Map<String, List<PersonaRow>> byFolder = new TreeMap<>();
        List<PersonaRow> ungrouped = new ArrayList<>();
        for (PersonaRow r : rows) {
            String f = r.folder() != null ? r.folder().trim() : "";
            if (f.isEmpty()) ungrouped.add(r);
            else byFolder.computeIfAbsent(f, k -> new ArrayList<>()).add(r);
        }
        for (Map.Entry<String, List<PersonaRow>> e : byFolder.entrySet()) {
            b.append("<h3 class=\"folder\">").append(esc(e.getKey().replace("/", " / "))).append("</h3>");
            e.getValue().sort(java.util.Comparator.comparingInt(PersonaRow::order));
            for (PersonaRow r : e.getValue()) personaCard(b, r, template, enemy);
        }
        if (!ungrouped.isEmpty()) {
            if (!byFolder.isEmpty()) b.append("<h3 class=\"folder\">Sans dossier</h3>");
            ungrouped.sort(java.util.Comparator.comparingInt(PersonaRow::order));
            for (PersonaRow r : ungrouped) personaCard(b, r, template, enemy);
        }
    }

    private void personaCard(StringBuilder b, PersonaRow r, List<TemplateField> template, boolean enemy) {
        // Mise en page en TABLE (portrait | contenu) : openhtmltopdf gere mal le float
        // + overflow (le texte se superposait au portrait).
        b.append("<div class=\"card\"><table class=\"persona\"><tr>");
        String portrait = imageUri(r.portraitId(), PORTRAIT_MAX);
        if (portrait != null) {
            b.append("<td class=\"persona-portrait\"><img src=\"").append(portrait).append("\"/></td>");
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
                for (Map.Entry<String, String> s : clean.entrySet()) {
                    b.append("<tr><th>").append(esc(s.getKey())).append("</th><td>")
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

    // ----- Lore (pages groupees par dossier) -----

    private void lore(StringBuilder b, StringBuilder bm, CampaignJpaEntity campaign) {
        String loreId = campaign.getLoreId();
        if (loreId == null || loreId.isBlank()) return;
        Long lid;
        try { lid = Long.parseLong(loreId); } catch (NumberFormatException ex) { return; }

        List<PageJpaEntity> pages = pageRepo.findByLoreId(lid);
        if (pages.isEmpty()) return;

        b.append("<h1 class=\"part\" id=\"part-lore\">Lore</h1>");
        bm.append(bookmark("Lore", "part-lore", null));

        // Chemins de dossiers (LoreNode) + templates par id.
        Map<Long, LoreNodeJpaEntity> nodes = new HashMap<>();
        for (LoreNodeJpaEntity n : loreNodeRepo.findByLoreId(lid)) nodes.put(n.getId(), n);
        Map<Long, TemplateJpaEntity> templates = new HashMap<>();
        for (TemplateJpaEntity t : templateRepo.findByLoreId(lid)) templates.put(t.getId(), t);

        Map<String, List<PageJpaEntity>> byPath = new TreeMap<>();
        for (PageJpaEntity p : pages) {
            byPath.computeIfAbsent(nodePath(p.getNodeId(), nodes), k -> new ArrayList<>()).add(p);
        }
        for (Map.Entry<String, List<PageJpaEntity>> e : byPath.entrySet()) {
            String label = e.getKey().isEmpty() ? "Sans dossier" : e.getKey();
            b.append("<h3 class=\"folder\">").append(esc(label)).append("</h3>");
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
                    if (inner != null && labels != null) {
                        StringBuilder rows = new StringBuilder();
                        for (String label : labels) {
                            String v = inner.get(label);
                            if (notBlank(v)) rows.append("<tr><th>").append(esc(label)).append("</th><td>")
                                    .append(esc(v)).append("</td></tr>");
                        }
                        if (rows.length() > 0) {
                            b.append("<div class=\"field-label\">").append(esc(f.getName()))
                                    .append("</div><table class=\"stats-table\"><tbody>").append(rows)
                                    .append("</tbody></table>");
                        }
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
                        for (Map<String, String> row : data) {
                            b.append("<tr>");
                            for (String col : cols) b.append("<td>").append(esc(row.get(col))).append("</td>");
                            b.append("</tr>");
                        }
                        b.append("</tbody></table>");
                    }
                }
            }
        }
    }

    /** Bloc "label + valeur multiligne" si la valeur est non vide. */
    private void block(StringBuilder b, String label, String value) {
        if (!notBlank(value)) return;
        b.append("<div class=\"field\"><div class=\"field-label\">").append(esc(label))
                .append("</div><div class=\"field-value\">").append(multiline(value)).append("</div></div>");
    }

    /** Galerie d'illustrations (liste d'ids d'images) -> blocs image. */
    private void illustrations(StringBuilder b, List<String> imageIds) {
        if (imageIds == null) return;
        for (String id : imageIds) {
            String uri = imageUri(id, ILLUSTRATION_MAX);
            if (uri != null) b.append("<div class=\"illus\"><img src=\"").append(uri).append("\"/></div>");
        }
    }

    // ====================================================================== Images

    /** Data-URI JPEG redimensionne d'une image LoreMind, ou null. */
    private String imageUri(String imageId, int maxDim) {
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

    /** Data-URI d'une battlemap (fichier stocke) seulement si c'est une image (pas une video). */
    private String fileImageUri(String fileId) {
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
    private String encode(InputStream in, int maxDim, String ref) {
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
            return "data:image/jpeg;base64," + Base64.getEncoder().encodeToString(out.toByteArray());
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

    /** Un signet PDF (avec enfants optionnels fournis par `children`). */
    private static String bookmark(String name, String anchor, java.util.function.Supplier<String> children) {
        String inner = children != null ? children.get() : "";
        return "<bookmark name=\"" + esc(name == null ? "" : name) + "\" href=\"#" + anchor + "\">" + inner + "</bookmark>";
    }

    /** Echappe le texte pour XHTML et retire les caracteres interdits en XML 1.0. */
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
                default -> b.append(c);
            }
        }
        return b.toString();
    }

    /** Comme esc, mais les sauts de ligne deviennent des &lt;br/&gt;. */
    private static String multiline(String s) {
        if (s == null) return "";
        return esc(s.replace("\r\n", "\n").replace('\r', '\n')).replace("\n", "<br/>");
    }

    // CSS print (CSS 2.1 + paged media supporte par openhtmltopdf : pas de flexbox/grid).
    private static final String CSS = """
        @page { size: A4; margin: 2cm 1.7cm;
          @bottom-center { content: counter(page); font-size: 9pt; color: #999; } }
        body { font-family: 'Helvetica', sans-serif; font-size: 10.5pt; color: #222; line-height: 1.45; }
        .cover { text-align: center; padding-top: 7cm; page-break-after: always; }
        .cover .subtitle { font-size: 12pt; letter-spacing: .3em; text-transform: uppercase; color: #8a7bc8; }
        .cover-title { font-size: 32pt; text-transform: uppercase; letter-spacing: .03em; color: #2e2a4a; margin: .4cm 0; border: none; }
        .cover-desc { margin: 1cm auto 0; max-width: 13cm; color: #444; text-align: left; }
        .cover .meta { margin-top: 1.2cm; color: #777; font-size: 10pt; }
        h1.part { page-break-before: always; font-size: 21pt; text-transform: uppercase; letter-spacing: .05em;
          color: #2e2a4a; border-bottom: 2pt solid #8a7bc8; padding-bottom: 3pt; margin: 0 0 .5cm; }
        h2 { font-size: 16pt; color: #4a3f7a; margin: 1.1em 0 .3em; }
        h3.folder { color: #8a7bc8; text-transform: uppercase; letter-spacing: .04em; font-size: 11pt;
          border-bottom: 1pt dotted #ccc; margin-top: 1em; }
        /* Hierarchie narrative : Arc (bandeau) > Quete (en-tete teinte) > Scene (carte). */
        .arc { margin: .5cm 0 .3cm; }
        .arc-head { background: #2e2a4a; color: #fff; font-size: 15pt; font-weight: bold;
          padding: .22cm .4cm; border-radius: 4pt; margin: 0 0 .35cm; }
        .quest { margin: .5cm 0 .35cm; }
        .quest-head { background: #f1eef9; border-left: 5pt solid #8a7bc8; padding: .15cm .4cm;
          font-size: 13pt; font-weight: bold; color: #463b78; }
        .scene { margin: .3cm 0 .35cm .35cm; border: 1pt solid #e6e6ee; border-left: 3pt solid #9bb06a;
          border-radius: 4pt; padding: .25cm .4cm; background: #fbfbfd; }
        .scene-head { font-size: 12pt; font-weight: bold; color: #5a6e3a; margin-bottom: .12cm; }
        .eyebrow { display: block; font-size: 7pt; text-transform: uppercase; letter-spacing: .15em;
          font-weight: normal; color: #9182bd; }
        .eyebrow-light { color: #c9c0e8; }
        .field { margin: .22cm 0; }
        .field-label { font-size: 8pt; text-transform: uppercase; letter-spacing: .07em;
          color: #8076a3; font-weight: bold; margin-bottom: .03cm; }
        .field-value { color: #222; }
        .card { page-break-inside: avoid; border: 1pt solid #e2e2e2; border-radius: 4pt;
          padding: .35cm .4cm; margin: .35cm 0; background: #fafafa; }
        .card-body { display: block; }
        .persona { width: 100%; border-collapse: collapse; }
        .persona-portrait { width: 3cm; vertical-align: top; padding: 0 .45cm 0 0; }
        .persona-portrait img { width: 3cm; border: 1pt solid #ccc; border-radius: 3pt; }
        .persona-content { vertical-align: top; }
        .persona-name { font-size: 12pt; font-weight: bold; color: #2e2a4a; margin-bottom: .12cm; }
        .level { font-size: 9pt; color: #8a7bc8; font-weight: normal; }
        .illus { margin: .3cm 0; }
        .illus img { width: 100%; border: 1pt solid #ccc; border-radius: 3pt; }
        .stats-table { width: 100%; border-collapse: collapse; font-size: 9pt; margin: .15cm 0 .3cm; }
        .stats-table th, .stats-table td { border: 1pt solid #e0e0e0; padding: 2pt 5pt; text-align: left; vertical-align: top; }
        .stats-table th { background: #f0eef7; width: 35%; color: #555; }
        """;
}
