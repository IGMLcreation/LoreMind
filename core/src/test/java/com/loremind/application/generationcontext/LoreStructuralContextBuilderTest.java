package com.loremind.application.generationcontext;

import com.loremind.domain.generationcontext.LoreStructuralContext;
import com.loremind.domain.lorecontext.Lore;
import com.loremind.domain.lorecontext.LoreNode;
import com.loremind.domain.lorecontext.Page;
import com.loremind.domain.lorecontext.Template;
import com.loremind.domain.lorecontext.ports.LoreNodeRepository;
import com.loremind.domain.lorecontext.ports.LoreRepository;
import com.loremind.domain.lorecontext.ports.PageRepository;
import com.loremind.domain.lorecontext.ports.TemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test unitaire pour LoreStructuralContextBuilder.
 * Couvre la projection LoreContext → GenerationContext : construction du
 * dossier→pages, résolution template/relatedPages, troncature des valeurs,
 * filtrage des valeurs vides, et extraction unique des tags.
 */
@ExtendWith(MockitoExtension.class)
public class LoreStructuralContextBuilderTest {

    @Mock private LoreRepository loreRepository;
    @Mock private LoreNodeRepository loreNodeRepository;
    @Mock private PageRepository pageRepository;
    @Mock private TemplateRepository templateRepository;

    @InjectMocks private LoreStructuralContextBuilder builder;

    private Lore lore;

    @BeforeEach
    void setUp() {
        lore = Lore.builder().id("lore-1").name("Aetheria").description("Monde aérien").build();
    }

    @Test
    void testBuild_LoreNotFound_ThrowsOnStrict() {
        when(loreRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> builder.build("missing"));
    }

    @Test
    void testBuildOptional_LoreNotFound_ReturnsEmpty() {
        when(loreRepository.findById("missing")).thenReturn(Optional.empty());

        assertTrue(builder.buildOptional("missing").isEmpty());
    }

    @Test
    void testBuild_EmptyLore() {
        when(loreRepository.findById("lore-1")).thenReturn(Optional.of(lore));
        when(loreNodeRepository.findByLoreId("lore-1")).thenReturn(List.of());
        when(pageRepository.findByLoreId("lore-1")).thenReturn(List.of());
        when(templateRepository.findByLoreId("lore-1")).thenReturn(List.of());

        LoreStructuralContext ctx = builder.build("lore-1");

        assertEquals("Aetheria", ctx.loreName());
        assertEquals("Monde aérien", ctx.loreDescription());
        assertTrue(ctx.folders().isEmpty());
        assertTrue(ctx.tags().isEmpty());
    }

    @Test
    void testBuild_FoldersAndPagesMapping() {
        LoreNode nodePnj = LoreNode.builder().id("n-1").name("PNJ").loreId("lore-1").build();
        LoreNode nodeLieux = LoreNode.builder().id("n-2").name("Lieux").loreId("lore-1").build();

        Template tpl = Template.builder().id("tpl-1").name("Personnage").build();

        Map<String, String> values = new LinkedHashMap<>();
        values.put("Histoire", "Il était une fois...");
        values.put("VideField", "   "); // blank → filtré
        values.put("NullField", null);   // null → filtré

        Page p1 = Page.builder()
                .id("p-1").loreId("lore-1").nodeId("n-1")
                .templateId("tpl-1").title("Alice")
                .values(values)
                .tags(List.of("hero", "magic"))
                .relatedPageIds(List.of("p-2", "p-ghost"))
                .build();
        Page p2 = Page.builder()
                .id("p-2").loreId("lore-1").nodeId("n-2")
                .templateId("tpl-missing").title("La Forêt")
                .values(Map.of())
                .tags(List.of("magic"))
                .build();

        when(loreRepository.findById("lore-1")).thenReturn(Optional.of(lore));
        when(loreNodeRepository.findByLoreId("lore-1")).thenReturn(List.of(nodePnj, nodeLieux));
        when(pageRepository.findByLoreId("lore-1")).thenReturn(List.of(p1, p2));
        when(templateRepository.findByLoreId("lore-1")).thenReturn(List.of(tpl));

        LoreStructuralContext ctx = builder.build("lore-1");

        assertEquals(2, ctx.folders().size());
        assertTrue(ctx.folders().containsKey("PNJ"));
        assertTrue(ctx.folders().containsKey("Lieux"));

        var pnjPages = ctx.folders().get("PNJ");
        assertEquals(1, pnjPages.size());
        var aliceSummary = pnjPages.get(0);
        assertEquals("Alice", aliceSummary.title());
        assertEquals("Personnage", aliceSummary.templateName());
        // Blank/null filtrés
        assertEquals(1, aliceSummary.values().size());
        assertEquals("Il était une fois...", aliceSummary.values().get("Histoire"));
        assertEquals(List.of("hero", "magic"), aliceSummary.tags());
        // p-2 resolved into title, p-ghost dropped silently
        assertEquals(List.of("La Forêt"), aliceSummary.relatedPageTitles());

        var forestSummary = ctx.folders().get("Lieux").get(0);
        // Template introuvable → "?"
        assertEquals("?", forestSummary.templateName());
        assertTrue(forestSummary.values().isEmpty());
        assertTrue(forestSummary.relatedPageTitles().isEmpty());

        // Tags uniques entre les 2 pages
        assertEquals(2, ctx.tags().size());
        assertTrue(ctx.tags().contains("hero"));
        assertTrue(ctx.tags().contains("magic"));
    }

    @Test
    void testBuild_TruncatesLongValues() {
        LoreNode node = LoreNode.builder().id("n-1").name("PNJ").build();
        Template tpl = Template.builder().id("tpl-1").name("Personnage").build();

        String longText = "a".repeat(600); // au-dessus du plafond 500
        Map<String, String> values = new HashMap<>();
        values.put("Histoire", longText);

        Page p = Page.builder()
                .id("p-1").loreId("lore-1").nodeId("n-1")
                .templateId("tpl-1").title("Alice")
                .values(values)
                .build();

        when(loreRepository.findById("lore-1")).thenReturn(Optional.of(lore));
        when(loreNodeRepository.findByLoreId("lore-1")).thenReturn(List.of(node));
        when(pageRepository.findByLoreId("lore-1")).thenReturn(List.of(p));
        when(templateRepository.findByLoreId("lore-1")).thenReturn(List.of(tpl));

        LoreStructuralContext ctx = builder.build("lore-1");

        String truncated = ctx.folders().get("PNJ").get(0).values().get("Histoire");
        assertNotNull(truncated);
        assertEquals(500 + 1, truncated.length()); // 500 + ellipse
        assertTrue(truncated.endsWith("…"));
    }

    @Test
    void testBuild_HandlesNullValuesAndTags() {
        LoreNode node = LoreNode.builder().id("n-1").name("PNJ").build();

        Page p = Page.builder()
                .id("p-1").loreId("lore-1").nodeId("n-1")
                .templateId(null).title("Alice")
                .values(null)
                .tags(null)
                .relatedPageIds(null)
                .build();

        when(loreRepository.findById("lore-1")).thenReturn(Optional.of(lore));
        when(loreNodeRepository.findByLoreId("lore-1")).thenReturn(List.of(node));
        when(pageRepository.findByLoreId("lore-1")).thenReturn(List.of(p));
        when(templateRepository.findByLoreId("lore-1")).thenReturn(List.of());

        LoreStructuralContext ctx = builder.build("lore-1");

        var summary = ctx.folders().get("PNJ").get(0);
        assertTrue(summary.values().isEmpty());
        assertTrue(summary.tags().isEmpty());
        assertTrue(summary.relatedPageTitles().isEmpty());
    }
}
