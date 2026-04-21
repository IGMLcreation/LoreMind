package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import org.junit.jupiter.api.BeforeEach;
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
import static org.mockito.Mockito.*;

/**
 * Test unitaire pour CampaignService.
 * Utilise des mocks pour les ports de sortie (CampaignRepository).
 * Teste la logique d'orchestration de la couche Application.
 */
@ExtendWith(MockitoExtension.class)
public class CampaignServiceTest {

    @Mock
    private CampaignRepository campaignRepository;

    @InjectMocks
    private CampaignService campaignService;

    private Campaign testCampaign;

    @BeforeEach
    void setUp() {
        testCampaign = Campaign.builder()
                .id("campaign-1")
                .name("Test Campaign")
                .description("Test Description")
                .loreId("lore-1")
                .arcsCount(0)
                .build();
    }

    @Test
    void testCreateCampaign_WithLoreId() {
        // Arrange
        CampaignService.CampaignData data = new CampaignService.CampaignData(
                "New Campaign",
                "Description",
                "lore-123"
        );
        when(campaignRepository.save(any(Campaign.class))).thenReturn(testCampaign);

        // Act
        Campaign result = campaignService.createCampaign(data);

        // Assert
        assertNotNull(result);
        verify(campaignRepository, times(1)).save(any(Campaign.class));
        assertEquals("lore-123", result.getLoreId());
    }

    @Test
    void testCreateCampaign_WithNullLoreId() {
        // Arrange
        CampaignService.CampaignData data = new CampaignService.CampaignData(
                "New Campaign",
                "Description",
                null
        );
        when(campaignRepository.save(any(Campaign.class))).thenReturn(testCampaign);

        // Act
        Campaign result = campaignService.createCampaign(data);

        // Assert
        assertNotNull(result);
        verify(campaignRepository, times(1)).save(any(Campaign.class));
        assertNull(result.getLoreId());
    }

    @Test
    void testCreateCampaign_WithBlankLoreId() {
        // Arrange
        CampaignService.CampaignData data = new CampaignService.CampaignData(
                "New Campaign",
                "Description",
                "   "
        );
        when(campaignRepository.save(any(Campaign.class))).thenReturn(testCampaign);

        // Act
        Campaign result = campaignService.createCampaign(data);

        // Assert
        assertNotNull(result);
        verify(campaignRepository, times(1)).save(any(Campaign.class));
        assertNull(result.getLoreId());
    }

    @Test
    void testGetCampaignById_Found() {
        // Arrange
        when(campaignRepository.findById("campaign-1")).thenReturn(Optional.of(testCampaign));

        // Act
        Optional<Campaign> result = campaignService.getCampaignById("campaign-1");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("Test Campaign", result.get().getName());
        verify(campaignRepository, times(1)).findById("campaign-1");
    }

    @Test
    void testGetCampaignById_NotFound() {
        // Arrange
        when(campaignRepository.findById("invalid-id")).thenReturn(Optional.empty());

        // Act
        Optional<Campaign> result = campaignService.getCampaignById("invalid-id");

        // Assert
        assertFalse(result.isPresent());
        verify(campaignRepository, times(1)).findById("invalid-id");
    }

    @Test
    void testGetAllCampaigns() {
        // Arrange
        Campaign campaign2 = Campaign.builder()
                .id("campaign-2")
                .name("Campaign 2")
                .build();
        when(campaignRepository.findAll()).thenReturn(List.of(testCampaign, campaign2));

        // Act
        List<Campaign> result = campaignService.getAllCampaigns();

        // Assert
        assertEquals(2, result.size());
        verify(campaignRepository, times(1)).findAll();
    }

    @Test
    void testUpdateCampaign_Success() {
        // Arrange
        CampaignService.CampaignData data = new CampaignService.CampaignData(
                "Updated Campaign",
                "Updated Description",
                "lore-456"
        );
        when(campaignRepository.findById("campaign-1")).thenReturn(Optional.of(testCampaign));
        when(campaignRepository.save(any(Campaign.class))).thenReturn(testCampaign);

        // Act
        Campaign result = campaignService.updateCampaign("campaign-1", data);

        // Assert
        assertNotNull(result);
        verify(campaignRepository, times(1)).findById("campaign-1");
        verify(campaignRepository, times(1)).save(any(Campaign.class));
    }

    @Test
    void testUpdateCampaign_NotFound() {
        // Arrange
        CampaignService.CampaignData data = new CampaignService.CampaignData(
                "Updated Campaign",
                "Updated Description",
                "lore-456"
        );
        when(campaignRepository.findById("invalid-id")).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> campaignService.updateCampaign("invalid-id", data)
        );
        assertEquals("Campaign non trouvé avec l'ID: invalid-id", exception.getMessage());
        verify(campaignRepository, times(1)).findById("invalid-id");
        verify(campaignRepository, never()).save(any());
    }

    @Test
    void testDeleteCampaign() {
        // Arrange
        doNothing().when(campaignRepository).deleteById("campaign-1");

        // Act
        campaignService.deleteCampaign("campaign-1");

        // Assert
        verify(campaignRepository, times(1)).deleteById("campaign-1");
    }

    @Test
    void testCampaignExists_True() {
        // Arrange
        when(campaignRepository.existsById("campaign-1")).thenReturn(true);

        // Act
        boolean result = campaignService.campaignExists("campaign-1");

        // Assert
        assertTrue(result);
        verify(campaignRepository, times(1)).existsById("campaign-1");
    }

    @Test
    void testCampaignExists_False() {
        // Arrange
        when(campaignRepository.existsById("invalid-id")).thenReturn(false);

        // Act
        boolean result = campaignService.campaignExists("invalid-id");

        // Assert
        assertFalse(result);
        verify(campaignRepository, times(1)).existsById("invalid-id");
    }

    @Test
    void testSearchCampaigns_WithValidQuery() {
        // Arrange
        when(campaignRepository.searchByName("Test")).thenReturn(List.of(testCampaign));

        // Act
        List<Campaign> result = campaignService.searchCampaigns("Test");

        // Assert
        assertEquals(1, result.size());
        verify(campaignRepository, times(1)).searchByName("Test");
    }

    @Test
    void testSearchCampaigns_WithNullQuery() {
        // Act
        List<Campaign> result = campaignService.searchCampaigns(null);

        // Assert
        assertTrue(result.isEmpty());
        verify(campaignRepository, never()).searchByName(anyString());
    }

    @Test
    void testSearchCampaigns_WithBlankQuery() {
        // Act
        List<Campaign> result = campaignService.searchCampaigns("   ");

        // Assert
        assertTrue(result.isEmpty());
        verify(campaignRepository, never()).searchByName(anyString());
    }

    @Test
    void testSearchCampaigns_WithTrim() {
        // Arrange
        when(campaignRepository.searchByName("Test")).thenReturn(List.of(testCampaign));

        // Act
        List<Campaign> result = campaignService.searchCampaigns("  Test  ");

        // Assert
        assertEquals(1, result.size());
        verify(campaignRepository, times(1)).searchByName("Test");
    }
}
