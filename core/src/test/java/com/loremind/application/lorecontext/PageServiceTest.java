package com.loremind.application.lorecontext;

import com.loremind.domain.lorecontext.Page;
import com.loremind.domain.lorecontext.ports.PageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test unitaire pour PageService.
 * Vérifie la création MVP (collections initialisées vides), le CRUD, les
 * copies défensives sur update, l'immuabilité de loreId/templateId, et
 * la gestion null-safe des collections.
 */
@ExtendWith(MockitoExtension.class)
public class PageServiceTest {

    @Mock private PageRepository pageRepository;

    @InjectMocks private PageService pageService;

    private Page existing;

    @BeforeEach
    void setUp() {
        existing = Page.builder()
                .id("p-1").loreId("lore-1").nodeId("n-1").templateId("tpl-1")
                .title("Alice")
                .values(new HashMap<>())
                .build();
    }

    @Test
    void testCreatePage_InitializesEmptyCollections() {
        when(pageRepository.save(any(Page.class))).thenAnswer(inv -> inv.getArgument(0));

        pageService.createPage("lore-1", "n-1", "tpl-1", "Alice");

        ArgumentCaptor<Page> captor = ArgumentCaptor.forClass(Page.class);
        verify(pageRepository).save(captor.capture());
        Page saved = captor.getValue();
        assertEquals("Alice", saved.getTitle());
        assertEquals("lore-1", saved.getLoreId());
        assertEquals("n-1", saved.getNodeId());
        assertEquals("tpl-1", saved.getTemplateId());
        assertNotNull(saved.getValues());
        assertTrue(saved.getValues().isEmpty());
        assertNotNull(saved.getTags());
        assertTrue(saved.getTags().isEmpty());
        assertNotNull(saved.getRelatedPageIds());
        assertTrue(saved.getRelatedPageIds().isEmpty());
    }

    @Test
    void testGetById_And_All_And_ByLore_And_ByNode() {
        when(pageRepository.findById("p-1")).thenReturn(Optional.of(existing));
        when(pageRepository.findAll()).thenReturn(List.of(existing));
        when(pageRepository.findByLoreId("lore-1")).thenReturn(List.of(existing));
        when(pageRepository.findByNodeId("n-1")).thenReturn(List.of(existing));

        assertTrue(pageService.getPageById("p-1").isPresent());
        assertEquals(1, pageService.getAllPages().size());
        assertEquals(1, pageService.getPagesByLoreId("lore-1").size());
        assertEquals(1, pageService.getPagesByNodeId("n-1").size());
    }

    @Test
    void testSearch_NullOrBlankReturnsEmpty() {
        assertTrue(pageService.searchPages(null).isEmpty());
        assertTrue(pageService.searchPages("   ").isEmpty());
        verifyNoInteractions(pageRepository);
    }

    @Test
    void testSearch_TrimsQuery() {
        when(pageRepository.searchByTitle("alice")).thenReturn(List.of(existing));

        pageService.searchPages("  alice  ");

        verify(pageRepository).searchByTitle("alice");
    }

    @Test
    void testUpdatePage_AppliesChangesAndKeepsImmutables() {
        Map<String, String> newValues = Map.of("Histoire", "Il...");
        Map<String, List<String>> newImages = Map.of("Portrait", List.of("img-1"));
        List<String> newTags = List.of("hero");
        List<String> newRelated = List.of("p-2");

        Page changes = Page.builder()
                .loreId("lore-OTHER")      // doit etre ignore
                .templateId("tpl-OTHER")   // doit etre ignore
                .nodeId("n-2")             // mutable : deplacement
                .title("Alice v2")
                .values(newValues)
                .imageValues(newImages)
                .notes("notes MJ")
                .tags(newTags)
                .relatedPageIds(newRelated)
                .build();

        when(pageRepository.findById("p-1")).thenReturn(Optional.of(existing));
        when(pageRepository.save(any(Page.class))).thenAnswer(inv -> inv.getArgument(0));

        Page result = pageService.updatePage("p-1", changes);

        assertEquals("Alice v2", result.getTitle());
        assertEquals("n-2", result.getNodeId());
        // loreId et templateId immuables
        assertEquals("lore-1", result.getLoreId());
        assertEquals("tpl-1", result.getTemplateId());
        assertEquals(newValues, result.getValues());
        assertNotSame(newValues, result.getValues()); // copie defensive
        assertEquals(newImages, result.getImageValues());
        assertNotSame(newImages, result.getImageValues());
        assertEquals("notes MJ", result.getNotes());
        assertEquals(newTags, result.getTags());
        assertNotSame(newTags, result.getTags());
        assertEquals(newRelated, result.getRelatedPageIds());
        assertNotSame(newRelated, result.getRelatedPageIds());
    }

    @Test
    void testUpdatePage_NullCollectionsBecomeEmpty() {
        Page changes = Page.builder()
                .nodeId("n-1").title("t")
                .values(null).imageValues(null).tags(null).relatedPageIds(null)
                .build();
        when(pageRepository.findById("p-1")).thenReturn(Optional.of(existing));
        when(pageRepository.save(any(Page.class))).thenAnswer(inv -> inv.getArgument(0));

        Page result = pageService.updatePage("p-1", changes);

        assertNotNull(result.getValues());
        assertTrue(result.getValues().isEmpty());
        assertNotNull(result.getImageValues());
        assertTrue(result.getImageValues().isEmpty());
        assertNotNull(result.getTags());
        assertTrue(result.getTags().isEmpty());
        assertNotNull(result.getRelatedPageIds());
        assertTrue(result.getRelatedPageIds().isEmpty());
    }

    @Test
    void testUpdatePage_NotFoundThrows() {
        when(pageRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> pageService.updatePage("missing", existing));
    }

    @Test
    void testDelete() {
        pageService.deletePage("p-1");
        verify(pageRepository).deleteById("p-1");
    }
}
