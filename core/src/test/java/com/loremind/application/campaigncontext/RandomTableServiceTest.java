package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.randomtable.RandomTable;
import com.loremind.domain.campaigncontext.randomtable.RandomTableEntry;
import com.loremind.domain.campaigncontext.ports.RandomTableGenerator;
import com.loremind.domain.campaigncontext.ports.RandomTableRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Test unitaire pour RandomTableService.
 * Mocks des ports (repository, générateur IA, campagne, système de jeu).
 */
@ExtendWith(MockitoExtension.class)
public class RandomTableServiceTest {

    @Mock
    private RandomTableRepository repository;
    @Mock
    private RandomTableGenerator generator;
    @Mock
    private CampaignContextFormatter campaignContextFormatter;

    @InjectMocks
    private RandomTableService service;

    private static RandomTableService.TableData data(Integer order, String campaignId) {
        return new RandomTableService.TableData(
                "Rencontres", "desc", "1d20", "dice",
                List.of(RandomTableEntry.builder().minRoll(1).maxRoll(10).label("Gobelins").build()),
                campaignId, order);
    }

    // --- createTable ---

    @Test
    void testCreateTable_WithExplicitOrder() {
        when(repository.save(any(RandomTable.class))).thenAnswer(inv -> inv.getArgument(0));

        RandomTable result = service.createTable(data(5, "camp-1"));

        assertEquals(5, result.getOrder());
        assertEquals("Rencontres", result.getName());
        assertEquals(1, result.getEntries().size());
        // Pas de calcul d'ordre auto puisque order fourni.
        verify(repository, never()).findByCampaignId(anyString());
    }

    @Test
    void testCreateTable_ComputesNextOrderWhenNull() {
        when(repository.findByCampaignId("camp-1")).thenReturn(List.of(
                RandomTable.builder().order(2).build(),
                RandomTable.builder().order(7).build()));
        when(repository.save(any(RandomTable.class))).thenAnswer(inv -> inv.getArgument(0));

        RandomTable result = service.createTable(data(null, "camp-1"));

        assertEquals(8, result.getOrder()); // max(2,7)+1
    }

    @Test
    void testCreateTable_NextOrderZeroWhenNoExisting() {
        when(repository.findByCampaignId("camp-1")).thenReturn(List.of());
        when(repository.save(any(RandomTable.class))).thenAnswer(inv -> inv.getArgument(0));

        RandomTable result = service.createTable(data(null, "camp-1"));

        assertEquals(0, result.getOrder()); // -1 + 1
    }

    @Test
    void testCreateTable_NullEntriesYieldsEmptyList() {
        when(repository.save(any(RandomTable.class))).thenAnswer(inv -> inv.getArgument(0));
        RandomTableService.TableData d = new RandomTableService.TableData(
                "T", null, "1d6", null, null, "camp-1", 0);

        RandomTable result = service.createTable(d);

        assertNotNull(result.getEntries());
        assertTrue(result.getEntries().isEmpty());
    }

    // --- read ---

    @Test
    void testGetTableById_Found() {
        RandomTable t = RandomTable.builder().id("t-1").name("T").build();
        when(repository.findById("t-1")).thenReturn(Optional.of(t));

        Optional<RandomTable> result = service.getTableById("t-1");

        assertTrue(result.isPresent());
        assertEquals("T", result.get().getName());
    }

    @Test
    void testGetTablesByCampaignId() {
        when(repository.findByCampaignId("camp-1")).thenReturn(List.of(RandomTable.builder().id("t-1").build()));

        List<RandomTable> result = service.getTablesByCampaignId("camp-1");

        assertEquals(1, result.size());
    }

    // --- updateTable ---

    @Test
    void testUpdateTable_Success() {
        RandomTable existing = RandomTable.builder().id("t-1").name("Old").order(3).build();
        when(repository.findById("t-1")).thenReturn(Optional.of(existing));
        when(repository.save(any(RandomTable.class))).thenAnswer(inv -> inv.getArgument(0));

        RandomTable result = service.updateTable("t-1", data(null, "camp-1"));

        assertEquals("Rencontres", result.getName());
        assertEquals("1d20", result.getDiceFormula());
        // order null dans data -> conserve l'ordre existant.
        assertEquals(3, result.getOrder());
    }

    @Test
    void testUpdateTable_AppliesOrderWhenProvided() {
        RandomTable existing = RandomTable.builder().id("t-1").name("Old").order(3).build();
        when(repository.findById("t-1")).thenReturn(Optional.of(existing));
        when(repository.save(any(RandomTable.class))).thenAnswer(inv -> inv.getArgument(0));

        RandomTable result = service.updateTable("t-1", data(9, "camp-1"));

        assertEquals(9, result.getOrder());
    }

    @Test
    void testUpdateTable_NotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateTable("missing", data(1, "camp-1")));
        assertEquals("Table aléatoire introuvable: missing", ex.getMessage());
        verify(repository, never()).save(any());
    }

    // --- delete ---

    @Test
    void testDeleteTable() {
        service.deleteTable("t-1");
        verify(repository).deleteById("t-1");
    }

    // --- reorder ---

    @Test
    void testReorderTables_AssignsPositions() {
        RandomTable a = RandomTable.builder().id("a").order(99).build();
        RandomTable b = RandomTable.builder().id("b").order(99).build();
        when(repository.findById("a")).thenReturn(Optional.of(a));
        when(repository.findById("b")).thenReturn(Optional.of(b));
        when(repository.save(any(RandomTable.class))).thenAnswer(inv -> inv.getArgument(0));

        service.reorderTables(List.of("a", "b"));

        assertEquals(0, a.getOrder());
        assertEquals(1, b.getOrder());
        verify(repository).save(a);
        verify(repository).save(b);
    }

    // --- searchTables ---

    @Test
    void testSearchTables_BlankReturnsEmpty() {
        assertTrue(service.searchTables(null).isEmpty());
        assertTrue(service.searchTables("   ").isEmpty());
        verify(repository, never()).searchByName(anyString());
    }

    @Test
    void testSearchTables_TrimsAndDelegates() {
        when(repository.searchByName("orc")).thenReturn(List.of(RandomTable.builder().id("t-1").build()));

        List<RandomTable> result = service.searchTables("  orc  ");

        assertEquals(1, result.size());
        verify(repository).searchByName("orc");
    }

    // --- generateProposal ---

    @Test
    void testGenerateProposal_DefaultsFormulaAndCopiesEntries() {
        RandomTableEntry e = RandomTableEntry.builder().minRoll(1).maxRoll(1).label("X").build();
        when(campaignContextFormatter.format("camp-1")).thenReturn("Campagne : Ma Campagne");
        when(generator.generate(eq("desc"), eq("1d20"), eq("Campagne : Ma Campagne")))
                .thenReturn(new RandomTableGenerator.GeneratedTable("Nom IA", "Desc IA", List.of(e)));

        RandomTable result = service.generateProposal("camp-1", "desc", " ");

        assertEquals("Nom IA", result.getName());
        assertEquals("1d20", result.getDiceFormula()); // formule blanche -> défaut 1d20
        assertEquals("camp-1", result.getCampaignId());
        assertEquals(1, result.getEntries().size());
    }

    @Test
    void testGenerateProposal_NullGeneratedEntries() {
        when(campaignContextFormatter.format("camp-1")).thenReturn("");
        when(generator.generate(anyString(), eq("2d6"), anyString()))
                .thenReturn(new RandomTableGenerator.GeneratedTable("N", "D", null));

        RandomTable result = service.generateProposal("camp-1", "desc", "2d6");

        assertNotNull(result.getEntries());
        assertTrue(result.getEntries().isEmpty());
    }

    @Test
    void testGenerateProposal_DelegatesContextToFormatter() {
        when(campaignContextFormatter.format("camp-1"))
                .thenReturn("Campagne : Camp — Une aventure\nSystème de jeu : D&D 5e");
        when(generator.generate(anyString(), anyString(), any()))
                .thenReturn(new RandomTableGenerator.GeneratedTable("N", "D", List.of()));

        service.generateProposal("camp-1", "desc", "1d8");

        verify(generator).generate(eq("desc"), eq("1d8"), eq("Campagne : Camp — Une aventure\nSystème de jeu : D&D 5e"));
    }

    // --- improviseRoll ---

    @Test
    void testImproviseRoll_DelegatesToGenerator() {
        when(campaignContextFormatter.format("camp-1")).thenReturn("");
        when(generator.improvise(eq("Rencontres"), eq("Gobelins"), eq("detail"), anyString()))
                .thenReturn("Un récit improvisé.");

        String result = service.improviseRoll("camp-1", "Rencontres", "Gobelins", "detail");

        assertEquals("Un récit improvisé.", result);
        verify(generator).improvise("Rencontres", "Gobelins", "detail", "");
    }
}
