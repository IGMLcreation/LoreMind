package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.CatalogItem;
import com.loremind.domain.campaigncontext.ItemCatalog;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.ItemCatalogGenerator;
import com.loremind.domain.campaigncontext.ports.ItemCatalogRepository;
import com.loremind.domain.gamesystemcontext.GameSystem;
import com.loremind.domain.gamesystemcontext.ports.GameSystemRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Test unitaire pour ItemCatalogService.
 * Mocks des ports (repository, générateur IA, campagne, système de jeu).
 */
@ExtendWith(MockitoExtension.class)
public class ItemCatalogServiceTest {

    @Mock
    private ItemCatalogRepository repository;
    @Mock
    private ItemCatalogGenerator generator;
    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private GameSystemRepository gameSystemRepository;

    @InjectMocks
    private ItemCatalogService service;

    private static ItemCatalogService.CatalogData data(Integer order, String campaignId) {
        return new ItemCatalogService.CatalogData(
                "Échoppe", "desc", "icon",
                List.of(CatalogItem.builder().name("Épée").price("50 po").build()),
                campaignId, order);
    }

    // --- createCatalog ---

    @Test
    void testCreateCatalog_WithExplicitOrder() {
        when(repository.save(any(ItemCatalog.class))).thenAnswer(inv -> inv.getArgument(0));

        ItemCatalog result = service.createCatalog(data(3, "camp-1"));

        assertEquals("Échoppe", result.getName());
        assertEquals(3, result.getOrder());
        assertEquals(1, result.getItems().size());
        verify(repository, never()).findByCampaignId(anyString());
    }

    @Test
    void testCreateCatalog_ComputesNextOrderWhenNull() {
        when(repository.findByCampaignId("camp-1")).thenReturn(List.of(
                ItemCatalog.builder().order(0).build(),
                ItemCatalog.builder().order(3).build()));
        when(repository.save(any(ItemCatalog.class))).thenAnswer(inv -> inv.getArgument(0));

        ItemCatalog result = service.createCatalog(data(null, "camp-1"));

        assertEquals(4, result.getOrder()); // max(0,3)+1
    }

    @Test
    void testCreateCatalog_NextOrderZeroWhenNoExisting() {
        when(repository.findByCampaignId("camp-1")).thenReturn(List.of());
        when(repository.save(any(ItemCatalog.class))).thenAnswer(inv -> inv.getArgument(0));

        ItemCatalog result = service.createCatalog(data(null, "camp-1"));

        assertEquals(0, result.getOrder());
    }

    @Test
    void testCreateCatalog_NullItemsBecomeEmpty() {
        when(repository.save(any(ItemCatalog.class))).thenAnswer(inv -> inv.getArgument(0));
        ItemCatalogService.CatalogData d = new ItemCatalogService.CatalogData(
                "C", null, null, null, "camp-1", 0);

        ItemCatalog result = service.createCatalog(d);

        assertNotNull(result.getItems());
        assertTrue(result.getItems().isEmpty());
    }

    // --- read ---

    @Test
    void testGetCatalogById_Found() {
        ItemCatalog c = ItemCatalog.builder().id("c-1").name("C").build();
        when(repository.findById("c-1")).thenReturn(Optional.of(c));

        Optional<ItemCatalog> result = service.getCatalogById("c-1");

        assertTrue(result.isPresent());
        assertEquals("C", result.get().getName());
    }

    @Test
    void testGetCatalogsByCampaignId() {
        when(repository.findByCampaignId("camp-1")).thenReturn(List.of(ItemCatalog.builder().id("c-1").build()));

        List<ItemCatalog> result = service.getCatalogsByCampaignId("camp-1");

        assertEquals(1, result.size());
    }

    // --- updateCatalog ---

    @Test
    void testUpdateCatalog_Success() {
        ItemCatalog existing = ItemCatalog.builder().id("c-1").name("Old").order(2).build();
        when(repository.findById("c-1")).thenReturn(Optional.of(existing));
        when(repository.save(any(ItemCatalog.class))).thenAnswer(inv -> inv.getArgument(0));

        ItemCatalog result = service.updateCatalog("c-1", data(null, "camp-1"));

        assertEquals("Échoppe", result.getName());
        assertEquals(1, result.getItems().size());
        // order null -> conserve l'ordre existant.
        assertEquals(2, result.getOrder());
    }

    @Test
    void testUpdateCatalog_AppliesOrderWhenProvided() {
        ItemCatalog existing = ItemCatalog.builder().id("c-1").name("Old").order(2).build();
        when(repository.findById("c-1")).thenReturn(Optional.of(existing));
        when(repository.save(any(ItemCatalog.class))).thenAnswer(inv -> inv.getArgument(0));

        ItemCatalog result = service.updateCatalog("c-1", data(6, "camp-1"));

        assertEquals(6, result.getOrder());
    }

    @Test
    void testUpdateCatalog_NotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.updateCatalog("missing", data(1, "camp-1")));
        assertEquals("Catalogue d'objets introuvable: missing", ex.getMessage());
        verify(repository, never()).save(any());
    }

    // --- delete ---

    @Test
    void testDeleteCatalog() {
        service.deleteCatalog("c-1");
        verify(repository).deleteById("c-1");
    }

    // --- searchCatalogs ---

    @Test
    void testSearchCatalogs_BlankReturnsEmpty() {
        assertTrue(service.searchCatalogs(null).isEmpty());
        assertTrue(service.searchCatalogs("   ").isEmpty());
        verify(repository, never()).searchByName(anyString());
    }

    @Test
    void testSearchCatalogs_TrimsAndDelegates() {
        when(repository.searchByName("epee")).thenReturn(List.of(ItemCatalog.builder().id("c-1").build()));

        List<ItemCatalog> result = service.searchCatalogs("  epee  ");

        assertEquals(1, result.size());
        verify(repository).searchByName("epee");
    }

    // --- generateProposal ---

    @Test
    void testGenerateProposal_CopiesItemsAndContext() {
        CatalogItem item = CatalogItem.builder().name("Potion").build();
        when(generator.generate(eq("desc"), anyString()))
                .thenReturn(new ItemCatalogGenerator.GeneratedCatalog("Nom IA", "Desc IA", List.of(item)));
        when(campaignRepository.findById("camp-1"))
                .thenReturn(Optional.of(Campaign.builder().id("camp-1").name("Camp").build()));

        ItemCatalog result = service.generateProposal("camp-1", "desc");

        assertEquals("Nom IA", result.getName());
        assertEquals("camp-1", result.getCampaignId());
        assertEquals(1, result.getItems().size());
    }

    @Test
    void testGenerateProposal_NullGeneratedItems() {
        when(generator.generate(anyString(), anyString()))
                .thenReturn(new ItemCatalogGenerator.GeneratedCatalog("N", "D", null));
        when(campaignRepository.findById("camp-1")).thenReturn(Optional.empty());

        ItemCatalog result = service.generateProposal("camp-1", "desc");

        assertNotNull(result.getItems());
        assertTrue(result.getItems().isEmpty());
    }

    @Test
    void testGenerateProposal_BuildsContextWithGameSystem() {
        when(campaignRepository.findById("camp-1")).thenReturn(Optional.of(Campaign.builder()
                .id("camp-1").name("Camp").description(" Aventure ").gameSystemId("gs-1").build()));
        when(gameSystemRepository.findById("gs-1"))
                .thenReturn(Optional.of(GameSystem.builder().id("gs-1").name("Pathfinder").build()));
        when(generator.generate(anyString(), any()))
                .thenReturn(new ItemCatalogGenerator.GeneratedCatalog("N", "D", List.of()));

        service.generateProposal("camp-1", "desc");

        ArgumentCaptor<String> ctxCaptor = ArgumentCaptor.forClass(String.class);
        verify(generator).generate(eq("desc"), ctxCaptor.capture());
        String ctx = ctxCaptor.getValue();
        assertTrue(ctx.contains("Camp"));
        assertTrue(ctx.contains("Aventure"));
        assertTrue(ctx.contains("Pathfinder"));
    }
}
