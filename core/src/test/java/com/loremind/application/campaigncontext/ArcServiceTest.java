package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
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
import static org.mockito.Mockito.*;

/**
 * Test unitaire pour ArcService.
 * Utilise des mocks pour les ports de sortie (ArcRepository).
 * Teste la logique d'orchestration de la couche Application.
 */
@ExtendWith(MockitoExtension.class)
public class ArcServiceTest {

    @Mock
    private ArcRepository arcRepository;

    @InjectMocks
    private ArcService arcService;

    private Arc testArc;

    @BeforeEach
    void setUp() {
        testArc = Arc.builder()
                .id("arc-1")
                .name("Test Arc")
                .description("Test Description")
                .campaignId("campaign-1")
                .order(1)
                .build();
    }

    @Test
    void testCreateArc() {
        // Arrange
        when(arcRepository.save(any(Arc.class))).thenReturn(testArc);

        // Act
        Arc result = arcService.createArc("New Arc", "Description", "campaign-1", 1);

        // Assert
        assertNotNull(result);
        verify(arcRepository, times(1)).save(any(Arc.class));
    }

    @Test
    void testGetArcById_Found() {
        // Arrange
        when(arcRepository.findById("arc-1")).thenReturn(Optional.of(testArc));

        // Act
        Optional<Arc> result = arcService.getArcById("arc-1");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("Test Arc", result.get().getName());
        verify(arcRepository, times(1)).findById("arc-1");
    }

    @Test
    void testGetArcById_NotFound() {
        // Arrange
        when(arcRepository.findById("invalid-id")).thenReturn(Optional.empty());

        // Act
        Optional<Arc> result = arcService.getArcById("invalid-id");

        // Assert
        assertFalse(result.isPresent());
        verify(arcRepository, times(1)).findById("invalid-id");
    }

    @Test
    void testGetAllArcs() {
        // Arrange
        Arc arc2 = Arc.builder()
                .id("arc-2")
                .name("Arc 2")
                .build();
        when(arcRepository.findAll()).thenReturn(List.of(testArc, arc2));

        // Act
        List<Arc> result = arcService.getAllArcs();

        // Assert
        assertEquals(2, result.size());
        verify(arcRepository, times(1)).findAll();
    }

    @Test
    void testGetArcsByCampaignId() {
        // Arrange
        when(arcRepository.findByCampaignId("campaign-1")).thenReturn(List.of(testArc));

        // Act
        List<Arc> result = arcService.getArcsByCampaignId("campaign-1");

        // Assert
        assertEquals(1, result.size());
        verify(arcRepository, times(1)).findByCampaignId("campaign-1");
    }

    @Test
    void testUpdateArc_Success() {
        // Arrange
        Arc updatedArc = Arc.builder()
                .name("Updated Arc")
                .description("Updated Description")
                .order(2)
                .themes("Theme1, Theme2")
                .stakes("High stakes")
                .gmNotes("GM notes")
                .rewards("Rewards")
                .resolution("Resolution")
                .relatedPageIds(List.of("page-1"))
                .illustrationImageIds(List.of("image-1"))
                .build();
        when(arcRepository.findById("arc-1")).thenReturn(Optional.of(testArc));
        when(arcRepository.save(any(Arc.class))).thenReturn(testArc);

        // Act
        Arc result = arcService.updateArc("arc-1", updatedArc);

        // Assert
        assertNotNull(result);
        verify(arcRepository, times(1)).findById("arc-1");
        verify(arcRepository, times(1)).save(any(Arc.class));
    }

    @Test
    void testUpdateArc_NotFound() {
        // Arrange
        Arc updatedArc = Arc.builder()
                .name("Updated Arc")
                .build();
        when(arcRepository.findById("invalid-id")).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> arcService.updateArc("invalid-id", updatedArc)
        );
        assertEquals("Arc non trouvé avec l'ID: invalid-id", exception.getMessage());
        verify(arcRepository, times(1)).findById("invalid-id");
        verify(arcRepository, never()).save(any());
    }

    @Test
    void testDeleteArc() {
        // Arrange
        doNothing().when(arcRepository).deleteById("arc-1");

        // Act
        arcService.deleteArc("arc-1");

        // Assert
        verify(arcRepository, times(1)).deleteById("arc-1");
    }

    @Test
    void testArcExists_True() {
        // Arrange
        when(arcRepository.existsById("arc-1")).thenReturn(true);

        // Act
        boolean result = arcService.arcExists("arc-1");

        // Assert
        assertTrue(result);
        verify(arcRepository, times(1)).existsById("arc-1");
    }

    @Test
    void testArcExists_False() {
        // Arrange
        when(arcRepository.existsById("invalid-id")).thenReturn(false);

        // Act
        boolean result = arcService.arcExists("invalid-id");

        // Assert
        assertFalse(result);
        verify(arcRepository, times(1)).existsById("invalid-id");
    }
}
