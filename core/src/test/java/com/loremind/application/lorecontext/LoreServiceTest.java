package com.loremind.application.lorecontext;

import com.loremind.domain.lorecontext.Lore;
import com.loremind.domain.lorecontext.ports.LoreNodeRepository;
import com.loremind.domain.lorecontext.ports.LoreRepository;
import com.loremind.domain.lorecontext.ports.PageRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test unitaire pour LoreService.
 * Vérifie le CRUD, l'enrichissement à la volée des compteurs nodeCount/pageCount,
 * et le comportement défensif de la recherche.
 */
@ExtendWith(MockitoExtension.class)
public class LoreServiceTest {

    @Mock private LoreRepository loreRepository;
    @Mock private LoreNodeRepository loreNodeRepository;
    @Mock private PageRepository pageRepository;

    @InjectMocks private LoreService loreService;

    private Lore testLore;

    @BeforeEach
    void setUp() {
        testLore = Lore.builder().id("lore-1").name("Aetheria").description("d").build();
    }

    @Test
    void testCreateLore_InitialCountsZero() {
        when(loreRepository.save(any(Lore.class))).thenReturn(testLore);

        loreService.createLore("Aetheria", "desc");

        ArgumentCaptor<Lore> captor = ArgumentCaptor.forClass(Lore.class);
        verify(loreRepository).save(captor.capture());
        Lore saved = captor.getValue();
        assertEquals("Aetheria", saved.getName());
        assertEquals("desc", saved.getDescription());
        assertEquals(0, saved.getNodeCount());
        assertEquals(0, saved.getPageCount());
    }

    @Test
    void testGetLoreById_EnrichesWithCounts() {
        when(loreRepository.findById("lore-1")).thenReturn(Optional.of(testLore));
        when(loreNodeRepository.countByLoreId("lore-1")).thenReturn(5L);
        when(pageRepository.countByLoreId("lore-1")).thenReturn(42L);

        Optional<Lore> result = loreService.getLoreById("lore-1");

        assertTrue(result.isPresent());
        assertEquals(5, result.get().getNodeCount());
        assertEquals(42, result.get().getPageCount());
    }

    @Test
    void testGetLoreById_NotFound() {
        when(loreRepository.findById("missing")).thenReturn(Optional.empty());

        assertTrue(loreService.getLoreById("missing").isEmpty());
        verifyNoInteractions(loreNodeRepository);
        verifyNoInteractions(pageRepository);
    }

    @Test
    void testGetAllLores_EnrichesEach() {
        Lore lore2 = Lore.builder().id("lore-2").name("B").build();
        when(loreRepository.findAll()).thenReturn(List.of(testLore, lore2));
        when(loreNodeRepository.countByLoreId("lore-1")).thenReturn(3L);
        when(loreNodeRepository.countByLoreId("lore-2")).thenReturn(7L);
        when(pageRepository.countByLoreId("lore-1")).thenReturn(10L);
        when(pageRepository.countByLoreId("lore-2")).thenReturn(20L);

        List<Lore> result = loreService.getAllLores();

        assertEquals(2, result.size());
        assertEquals(3, result.get(0).getNodeCount());
        assertEquals(10, result.get(0).getPageCount());
        assertEquals(7, result.get(1).getNodeCount());
        assertEquals(20, result.get(1).getPageCount());
    }

    @Test
    void testSearchLores_NullOrBlankReturnsEmpty() {
        assertTrue(loreService.searchLores(null).isEmpty());
        assertTrue(loreService.searchLores("   ").isEmpty());
        verifyNoInteractions(loreRepository);
    }

    @Test
    void testSearchLores_TrimsQuery() {
        when(loreRepository.searchByName("aet")).thenReturn(List.of(testLore));

        List<Lore> result = loreService.searchLores("  aet  ");

        assertEquals(1, result.size());
        verify(loreRepository).searchByName("aet");
    }

    @Test
    void testUpdateLore_Success() {
        when(loreRepository.findById("lore-1")).thenReturn(Optional.of(testLore));
        when(loreRepository.save(any(Lore.class))).thenAnswer(inv -> inv.getArgument(0));

        Lore updated = loreService.updateLore("lore-1", "New Name", "New Desc");

        assertEquals("New Name", updated.getName());
        assertEquals("New Desc", updated.getDescription());
        verify(loreRepository).save(testLore);
    }

    @Test
    void testUpdateLore_NotFoundThrows() {
        when(loreRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> loreService.updateLore("missing", "n", "d"));
        verify(loreRepository, never()).save(any());
    }

    @Test
    void testDeleteLore_DelegatesToRepository() {
        loreService.deleteLore("lore-1");
        verify(loreRepository).deleteById("lore-1");
    }
}
