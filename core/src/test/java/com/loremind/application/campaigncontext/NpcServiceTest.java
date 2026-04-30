package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Npc;
import com.loremind.domain.campaigncontext.ports.NpcRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test unitaire pour NpcService.
 * Couvre la création (avec auto-calcul de l'order), la lecture, la mise à jour
 * (incl. cas non trouvé), la suppression, et le calcul d'order.
 */
@ExtendWith(MockitoExtension.class)
public class NpcServiceTest {

    @Mock
    private NpcRepository npcRepository;

    @InjectMocks
    private NpcService npcService;

    private Npc testNpc;

    @BeforeEach
    void setUp() {
        testNpc = Npc.builder()
                .id("npc-1")
                .name("Borin le forgeron")
                .values(new java.util.HashMap<>(Map.of("Notes", "Forgeron nain")))
                .campaignId("camp-1")
                .order(1)
                .build();
    }

    @Test
    void testCreateNpc_WithExplicitOrder() {
        when(npcRepository.save(any(Npc.class))).thenReturn(testNpc);

        Npc result = npcService.createNpc(
                new NpcService.NpcData("Borin le forgeron", null, null,
                        Map.of("Notes", "Borin"), null, "camp-1", 5));

        assertNotNull(result);
        ArgumentCaptor<Npc> captor = ArgumentCaptor.forClass(Npc.class);
        verify(npcRepository).save(captor.capture());
        assertEquals(5, captor.getValue().getOrder());
    }

    @Test
    void testCreateNpc_AutoComputesNextOrder_WhenNullProvided() {
        // Existant : 2 PNJ avec orders 0 et 3 → next = 4
        Npc a = Npc.builder().id("a").campaignId("camp-1").order(0).build();
        Npc b = Npc.builder().id("b").campaignId("camp-1").order(3).build();
        when(npcRepository.findByCampaignId("camp-1")).thenReturn(List.of(a, b));
        when(npcRepository.save(any(Npc.class))).thenReturn(testNpc);

        npcService.createNpc(new NpcService.NpcData("Nouveau", null, null, null, null, "camp-1", null));

        ArgumentCaptor<Npc> captor = ArgumentCaptor.forClass(Npc.class);
        verify(npcRepository).save(captor.capture());
        assertEquals(4, captor.getValue().getOrder());
    }

    @Test
    void testCreateNpc_FirstNpcGetsOrderZero() {
        when(npcRepository.findByCampaignId("camp-1")).thenReturn(List.of());
        when(npcRepository.save(any(Npc.class))).thenReturn(testNpc);

        npcService.createNpc(new NpcService.NpcData("Premier", null, null, null, null, "camp-1", null));

        ArgumentCaptor<Npc> captor = ArgumentCaptor.forClass(Npc.class);
        verify(npcRepository).save(captor.capture());
        assertEquals(0, captor.getValue().getOrder());
    }

    @Test
    void testGetNpcById_Found() {
        when(npcRepository.findById("npc-1")).thenReturn(Optional.of(testNpc));

        Optional<Npc> result = npcService.getNpcById("npc-1");

        assertTrue(result.isPresent());
        assertEquals("Borin le forgeron", result.get().getName());
    }

    @Test
    void testGetNpcById_NotFound() {
        when(npcRepository.findById("missing")).thenReturn(Optional.empty());

        Optional<Npc> result = npcService.getNpcById("missing");

        assertFalse(result.isPresent());
    }

    @Test
    void testGetNpcsByCampaignId_DelegatesToRepository() {
        Npc a = Npc.builder().id("a").campaignId("camp-1").order(1).build();
        Npc b = Npc.builder().id("b").campaignId("camp-1").order(2).build();
        when(npcRepository.findByCampaignId("camp-1")).thenReturn(List.of(a, b));

        List<Npc> result = npcService.getNpcsByCampaignId("camp-1");

        assertEquals(2, result.size());
        verify(npcRepository).findByCampaignId("camp-1");
    }

    @Test
    void testUpdateNpc_Success() {
        when(npcRepository.findById("npc-1")).thenReturn(Optional.of(testNpc));
        when(npcRepository.save(any(Npc.class))).thenAnswer(inv -> inv.getArgument(0));

        Npc result = npcService.updateNpc("npc-1",
                new NpcService.NpcData("Borin renommé", null, null,
                        Map.of("Notes", "v2"), null, "camp-1", 7));

        assertEquals("Borin renommé", result.getName());
        assertEquals("v2", result.getValues().get("Notes"));
        assertEquals(7, result.getOrder());
    }

    @Test
    void testUpdateNpc_OrderNullPreservesExistingOrder() {
        when(npcRepository.findById("npc-1")).thenReturn(Optional.of(testNpc));
        when(npcRepository.save(any(Npc.class))).thenAnswer(inv -> inv.getArgument(0));

        Npc result = npcService.updateNpc("npc-1",
                new NpcService.NpcData("Borin", null, null,
                        Map.of("Notes", "txt"), null, "camp-1", null));

        // testNpc avait order=1 → préservé
        assertEquals(1, result.getOrder());
    }

    @Test
    void testUpdateNpc_NotFoundThrows() {
        when(npcRepository.findById("missing")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> npcService.updateNpc("missing",
                        new NpcService.NpcData("x", null, null, null, null, "camp-1", null)));
        assertTrue(ex.getMessage().contains("missing"));
        verify(npcRepository, never()).save(any());
    }

    @Test
    void testDeleteNpc_DelegatesToRepository() {
        npcService.deleteNpc("npc-1");
        verify(npcRepository).deleteById("npc-1");
    }
}
