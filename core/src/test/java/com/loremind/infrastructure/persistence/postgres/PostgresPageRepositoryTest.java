package com.loremind.infrastructure.persistence.postgres;

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
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests d'integration pour PostgresPageRepository.
 * Valide la persistance des 4 collections JSONB : values, imageValues, tags,
 * relatedPageIds — via les AttributeConverter.
 */
@SpringBootTest
@Transactional
class PostgresPageRepositoryTest {

    @Autowired private PageRepository pageRepository;
    @Autowired private LoreRepository loreRepository;
    @Autowired private LoreNodeRepository nodeRepository;
    @Autowired private TemplateRepository templateRepository;

    private String loreId;
    private String nodeId;
    private String templateId;

    @BeforeEach
    void setUp() {
        loreId = loreRepository.save(Lore.builder().name("Lore").description("").build()).getId();
        nodeId = nodeRepository.save(LoreNode.builder().name("Node").loreId(loreId).build()).getId();
        templateId = templateRepository.save(Template.builder()
                .loreId(loreId).name("Tpl").fields(List.of()).build()).getId();
    }

    @Test
    void save_persistsPageWithAllJsonbFields_andRoundTrips() {
        Page page = Page.builder()
                .loreId(loreId)
                .nodeId(nodeId)
                .templateId(templateId)
                .title("Thorin")
                .values(Map.of("histoire", "Nee sous une etoile rouge", "motto", "Jamais"))
                .imageValues(Map.of("portraits", List.of("img-1", "img-2")))
                .notes("Secret MJ")
                .tags(List.of("pnj", "allie"))
                .relatedPageIds(List.of("page-x"))
                .build();

        Page saved = pageRepository.save(page);
        assertNotNull(saved.getId());

        Page r = pageRepository.findById(saved.getId()).orElseThrow();
        assertEquals("Thorin", r.getTitle());
        assertEquals("Nee sous une etoile rouge", r.getValues().get("histoire"));
        assertEquals(List.of("img-1", "img-2"), r.getImageValues().get("portraits"));
        assertEquals("Secret MJ", r.getNotes());
        assertEquals(2, r.getTags().size());
        assertEquals(List.of("page-x"), r.getRelatedPageIds());
    }

    @Test
    void findByLoreId_returnsOnlyPagesOfThatLore() {
        Lore other = loreRepository.save(Lore.builder().name("Other").description("").build());
        String otherNode = nodeRepository.save(LoreNode.builder().name("N").loreId(other.getId()).build()).getId();
        String otherTpl = templateRepository.save(Template.builder().loreId(other.getId()).name("T").fields(List.of()).build()).getId();

        pageRepository.save(buildMinimal(loreId, nodeId, templateId, "A"));
        pageRepository.save(buildMinimal(loreId, nodeId, templateId, "B"));
        pageRepository.save(buildMinimal(other.getId(), otherNode, otherTpl, "C"));

        assertEquals(2, pageRepository.findByLoreId(loreId).size());
    }

    @Test
    void findByNodeId_returnsPagesInThatFolder() {
        String otherNode = nodeRepository.save(LoreNode.builder().name("Other").loreId(loreId).build()).getId();
        pageRepository.save(buildMinimal(loreId, nodeId, templateId, "A"));
        pageRepository.save(buildMinimal(loreId, nodeId, templateId, "B"));
        pageRepository.save(buildMinimal(loreId, otherNode, templateId, "C"));

        assertEquals(2, pageRepository.findByNodeId(nodeId).size());
    }

    @Test
    void countByLoreId_matchesSaveCount() {
        pageRepository.save(buildMinimal(loreId, nodeId, templateId, "A"));
        pageRepository.save(buildMinimal(loreId, nodeId, templateId, "B"));
        pageRepository.save(buildMinimal(loreId, nodeId, templateId, "C"));

        assertEquals(3, pageRepository.countByLoreId(loreId));
    }

    @Test
    void searchByTitle_findsHits() {
        pageRepository.save(buildMinimal(loreId, nodeId, templateId, "Thorin"));
        pageRepository.save(buildMinimal(loreId, nodeId, templateId, "Thalia"));
        pageRepository.save(buildMinimal(loreId, nodeId, templateId, "Garde"));

        List<Page> hits = pageRepository.searchByTitle("tho");
        assertTrue(hits.size() >= 1);
        assertTrue(hits.stream().anyMatch(p -> p.getTitle().equals("Thorin")));
    }

    @Test
    void deleteById_removesPage() {
        Page saved = pageRepository.save(buildMinimal(loreId, nodeId, templateId, "X"));
        assertTrue(pageRepository.existsById(saved.getId()));

        pageRepository.deleteById(saved.getId());

        assertFalse(pageRepository.existsById(saved.getId()));
    }

    @Test
    void save_nullCollections_areStoredAsEmpty_afterReload() {
        // Les converters convertissent null -> "{}" / "[]" donc le reload
        // rend une collection vide plutot que null.
        Page page = Page.builder()
                .loreId(loreId).nodeId(nodeId).templateId(templateId).title("Minimal")
                .values(null).imageValues(null).tags(null).relatedPageIds(null)
                .build();
        Page saved = pageRepository.save(page);

        Page r = pageRepository.findById(saved.getId()).orElseThrow();
        assertNotNull(r.getValues());
        assertTrue(r.getValues().isEmpty());
        assertNotNull(r.getImageValues());
        assertNotNull(r.getTags());
        assertNotNull(r.getRelatedPageIds());
    }

    // --- helper ------------------------------------------------------------

    private static Page buildMinimal(String loreId, String nodeId, String tplId, String title) {
        return Page.builder()
                .loreId(loreId).nodeId(nodeId).templateId(tplId).title(title)
                .values(Map.of()).imageValues(Map.of())
                .tags(List.of()).relatedPageIds(List.of())
                .build();
    }
}
