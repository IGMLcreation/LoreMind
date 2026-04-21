package com.loremind.application.lorecontext;

import com.loremind.domain.lorecontext.LoreNode;
import com.loremind.domain.lorecontext.ports.LoreNodeRepository;
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
 * Test unitaire pour LoreNodeService.
 * Vérifie le CRUD, le pattern Parameter Object pour update/create, et
 * l'immuabilité de loreId en update.
 */
@ExtendWith(MockitoExtension.class)
public class LoreNodeServiceTest {

    @Mock private LoreNodeRepository loreNodeRepository;

    @InjectMocks private LoreNodeService loreNodeService;

    private LoreNode existing;

    @BeforeEach
    void setUp() {
        existing = LoreNode.builder()
                .id("n-1").name("PNJ").icon("users")
                .parentId(null).loreId("lore-1")
                .build();
    }

    @Test
    void testCreateLoreNode_CopiesChanges() {
        LoreNode changes = LoreNode.builder()
                .name("Lieux").icon("map-pin").parentId("n-parent").loreId("lore-1")
                .build();
        when(loreNodeRepository.save(any(LoreNode.class))).thenAnswer(inv -> inv.getArgument(0));

        loreNodeService.createLoreNode(changes);

        ArgumentCaptor<LoreNode> captor = ArgumentCaptor.forClass(LoreNode.class);
        verify(loreNodeRepository).save(captor.capture());
        LoreNode saved = captor.getValue();
        assertEquals("Lieux", saved.getName());
        assertEquals("map-pin", saved.getIcon());
        assertEquals("n-parent", saved.getParentId());
        assertEquals("lore-1", saved.getLoreId());
    }

    @Test
    void testGetLoreNodeById() {
        when(loreNodeRepository.findById("n-1")).thenReturn(Optional.of(existing));

        assertTrue(loreNodeService.getLoreNodeById("n-1").isPresent());
    }

    @Test
    void testGetAll_AndByLoreId_AndByParentId() {
        when(loreNodeRepository.findAll()).thenReturn(List.of(existing));
        when(loreNodeRepository.findByLoreId("lore-1")).thenReturn(List.of(existing));
        when(loreNodeRepository.findByParentId("n-parent")).thenReturn(List.of(existing));

        assertEquals(1, loreNodeService.getAllLoreNodes().size());
        assertEquals(1, loreNodeService.getLoreNodesByLoreId("lore-1").size());
        assertEquals(1, loreNodeService.getLoreNodesByParentId("n-parent").size());
    }

    @Test
    void testSearch_NullOrBlankReturnsEmpty() {
        assertTrue(loreNodeService.searchLoreNodes(null).isEmpty());
        assertTrue(loreNodeService.searchLoreNodes("   ").isEmpty());
        verifyNoInteractions(loreNodeRepository);
    }

    @Test
    void testSearch_TrimsQuery() {
        when(loreNodeRepository.searchByName("pnj")).thenReturn(List.of(existing));

        loreNodeService.searchLoreNodes("  pnj  ");

        verify(loreNodeRepository).searchByName("pnj");
    }

    @Test
    void testUpdateLoreNode_AppliesChangesButKeepsLoreId() {
        LoreNode changes = LoreNode.builder()
                .name("Villes").icon("castle").parentId("n-parent")
                .loreId("lore-2") // tentative de migration - doit etre ignoree
                .build();
        when(loreNodeRepository.findById("n-1")).thenReturn(Optional.of(existing));
        when(loreNodeRepository.save(any(LoreNode.class))).thenAnswer(inv -> inv.getArgument(0));

        LoreNode result = loreNodeService.updateLoreNode("n-1", changes);

        assertEquals("Villes", result.getName());
        assertEquals("castle", result.getIcon());
        assertEquals("n-parent", result.getParentId());
        // loreId doit rester inchange (immutable)
        assertEquals("lore-1", result.getLoreId());
    }

    @Test
    void testUpdateLoreNode_NotFoundThrows() {
        when(loreNodeRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> loreNodeService.updateLoreNode("missing", existing));
    }

    @Test
    void testDelete() {
        loreNodeService.deleteLoreNode("n-1");
        verify(loreNodeRepository).deleteById("n-1");
    }
}
