package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Scene;
import com.loremind.domain.campaigncontext.SceneBranch;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
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
 * Test unitaire pour SceneService.
 * Utilise des mocks pour les ports de sortie (SceneRepository).
 * Teste la logique d'orchestration de la couche Application.
 */
@ExtendWith(MockitoExtension.class)
public class SceneServiceTest {

    @Mock
    private SceneRepository sceneRepository;

    @InjectMocks
    private SceneService sceneService;

    private Scene testScene;
    private Scene scene2;
    private Scene scene3;

    @BeforeEach
    void setUp() {
        testScene = Scene.builder()
                .id("scene-1")
                .name("Test Scene")
                .description("Test Description")
                .chapterId("chapter-1")
                .order(1)
                .build();

        scene2 = Scene.builder()
                .id("scene-2")
                .name("Scene 2")
                .chapterId("chapter-1")
                .order(2)
                .build();

        scene3 = Scene.builder()
                .id("scene-3")
                .name("Scene 3")
                .chapterId("chapter-1")
                .order(3)
                .build();
    }

    @Test
    void testCreateScene() {
        // Arrange
        when(sceneRepository.save(any(Scene.class))).thenReturn(testScene);

        // Act
        Scene result = sceneService.createScene("New Scene", "Description", "chapter-1", 1);

        // Assert
        assertNotNull(result);
        verify(sceneRepository, times(1)).save(any(Scene.class));
    }

    @Test
    void testGetSceneById_Found() {
        // Arrange
        when(sceneRepository.findById("scene-1")).thenReturn(Optional.of(testScene));

        // Act
        Optional<Scene> result = sceneService.getSceneById("scene-1");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("Test Scene", result.get().getName());
        verify(sceneRepository, times(1)).findById("scene-1");
    }

    @Test
    void testGetSceneById_NotFound() {
        // Arrange
        when(sceneRepository.findById("invalid-id")).thenReturn(Optional.empty());

        // Act
        Optional<Scene> result = sceneService.getSceneById("invalid-id");

        // Assert
        assertFalse(result.isPresent());
        verify(sceneRepository, times(1)).findById("invalid-id");
    }

    @Test
    void testGetAllScenes() {
        // Arrange
        when(sceneRepository.findAll()).thenReturn(List.of(testScene, scene2));

        // Act
        List<Scene> result = sceneService.getAllScenes();

        // Assert
        assertEquals(2, result.size());
        verify(sceneRepository, times(1)).findAll();
    }

    @Test
    void testGetScenesByChapterId() {
        // Arrange
        when(sceneRepository.findByChapterId("chapter-1")).thenReturn(List.of(testScene, scene2));

        // Act
        List<Scene> result = sceneService.getScenesByChapterId("chapter-1");

        // Assert
        assertEquals(2, result.size());
        verify(sceneRepository, times(1)).findByChapterId("chapter-1");
    }

    @Test
    void testUpdateScene_Success() {
        // Arrange
        Scene updatedScene = Scene.builder()
                .name("Updated Scene")
                .description("Updated Description")
                .order(2)
                .location("Tavern")
                .timing("Evening")
                .atmosphere("Cozy")
                .playerNarration("Narration")
                .gmSecretNotes("Secret")
                .choicesConsequences("Choices")
                .combatDifficulty("Medium")
                .enemies("Goblins")
                .relatedPageIds(List.of("page-1"))
                .illustrationImageIds(List.of("image-1"))
                .branches(List.of())
                .build();
        when(sceneRepository.findById("scene-1")).thenReturn(Optional.of(testScene));
        when(sceneRepository.save(any(Scene.class))).thenReturn(testScene);

        // Act
        Scene result = sceneService.updateScene("scene-1", updatedScene);

        // Assert
        assertNotNull(result);
        verify(sceneRepository, times(1)).findById("scene-1");
        verify(sceneRepository, times(1)).save(any(Scene.class));
    }

    @Test
    void testUpdateScene_NotFound() {
        // Arrange
        Scene updatedScene = Scene.builder()
                .name("Updated Scene")
                .build();
        when(sceneRepository.findById("invalid-id")).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> sceneService.updateScene("invalid-id", updatedScene)
        );
        assertEquals("Scene non trouvée avec l'ID: invalid-id", exception.getMessage());
        verify(sceneRepository, times(1)).findById("invalid-id");
        verify(sceneRepository, never()).save(any());
    }

    @Test
    void testUpdateScene_WithValidBranches() {
        // Arrange
        SceneBranch branch = SceneBranch.of("Go to scene 2", "scene-2");
        Scene updatedScene = Scene.builder()
                .name("Updated Scene")
                .branches(List.of(branch))
                .chapterId("chapter-1")
                .build();
        when(sceneRepository.findById("scene-1")).thenReturn(Optional.of(testScene));
        when(sceneRepository.findByChapterId("chapter-1")).thenReturn(List.of(testScene, scene2, scene3));
        when(sceneRepository.save(any(Scene.class))).thenReturn(testScene);

        // Act
        Scene result = sceneService.updateScene("scene-1", updatedScene);

        // Assert
        assertNotNull(result);
        verify(sceneRepository, times(1)).findById("scene-1");
        verify(sceneRepository, times(1)).save(any(Scene.class));
    }

    @Test
    void testUpdateScene_WithBranchToSelf() {
        // Arrange
        SceneBranch branch = SceneBranch.of("Self-reference", "scene-1");
        Scene updatedScene = Scene.builder()
                .name("Updated Scene")
                .branches(List.of(branch))
                .chapterId("chapter-1")
                .build();
        when(sceneRepository.findById("scene-1")).thenReturn(Optional.of(testScene));
        when(sceneRepository.findByChapterId("chapter-1")).thenReturn(List.of(testScene, scene2));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> sceneService.updateScene("scene-1", updatedScene)
        );
        assertEquals("Une scène ne peut pas se brancher sur elle-même", exception.getMessage());
        verify(sceneRepository, times(1)).findById("scene-1");
        verify(sceneRepository, never()).save(any());
    }

    @Test
    void testUpdateScene_WithBranchToDifferentChapter() {
        // Arrange
        SceneBranch branch = SceneBranch.of("Go to other chapter", "scene-other-chapter");
        Scene updatedScene = Scene.builder()
                .name("Updated Scene")
                .branches(List.of(branch))
                .chapterId("chapter-1")
                .build();
        when(sceneRepository.findById("scene-1")).thenReturn(Optional.of(testScene));
        when(sceneRepository.findByChapterId("chapter-1")).thenReturn(List.of(testScene, scene2));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> sceneService.updateScene("scene-1", updatedScene)
        );
        assertTrue(exception.getMessage().contains("n'appartient pas au même chapitre"));
        verify(sceneRepository, times(1)).findById("scene-1");
        verify(sceneRepository, never()).save(any());
    }

    @Test
    void testUpdateScene_WithBranchNullTarget() {
        // Arrange
        SceneBranch branch = SceneBranch.of("Null target", null);
        Scene updatedScene = Scene.builder()
                .name("Updated Scene")
                .branches(List.of(branch))
                .chapterId("chapter-1")
                .build();
        when(sceneRepository.findById("scene-1")).thenReturn(Optional.of(testScene));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> sceneService.updateScene("scene-1", updatedScene)
        );
        assertEquals("Une branche doit avoir une scène de destination", exception.getMessage());
        verify(sceneRepository, times(1)).findById("scene-1");
        verify(sceneRepository, never()).save(any());
    }

    @Test
    void testUpdateScene_WithBranchBlankTarget() {
        // Arrange
        SceneBranch branch = SceneBranch.of("Blank target", "   ");
        Scene updatedScene = Scene.builder()
                .name("Updated Scene")
                .branches(List.of(branch))
                .chapterId("chapter-1")
                .build();
        when(sceneRepository.findById("scene-1")).thenReturn(Optional.of(testScene));

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> sceneService.updateScene("scene-1", updatedScene)
        );
        assertEquals("Une branche doit avoir une scène de destination", exception.getMessage());
        verify(sceneRepository, times(1)).findById("scene-1");
        verify(sceneRepository, never()).save(any());
    }

    @Test
    void testUpdateScene_WithEmptyBranches() {
        // Arrange
        Scene updatedScene = Scene.builder()
                .name("Updated Scene")
                .branches(List.of())
                .chapterId("chapter-1")
                .build();
        when(sceneRepository.findById("scene-1")).thenReturn(Optional.of(testScene));
        when(sceneRepository.save(any(Scene.class))).thenReturn(testScene);

        // Act
        Scene result = sceneService.updateScene("scene-1", updatedScene);

        // Assert
        assertNotNull(result);
        verify(sceneRepository, times(1)).findById("scene-1");
        verify(sceneRepository, times(1)).save(any(Scene.class));
    }

    @Test
    void testDeleteScene() {
        // Arrange
        doNothing().when(sceneRepository).deleteById("scene-1");

        // Act
        sceneService.deleteScene("scene-1");

        // Assert
        verify(sceneRepository, times(1)).deleteById("scene-1");
    }

    @Test
    void testSceneExists_True() {
        // Arrange
        when(sceneRepository.existsById("scene-1")).thenReturn(true);

        // Act
        boolean result = sceneService.sceneExists("scene-1");

        // Assert
        assertTrue(result);
        verify(sceneRepository, times(1)).existsById("scene-1");
    }

    @Test
    void testSceneExists_False() {
        // Arrange
        when(sceneRepository.existsById("invalid-id")).thenReturn(false);

        // Act
        boolean result = sceneService.sceneExists("invalid-id");

        // Assert
        assertFalse(result);
        verify(sceneRepository, times(1)).existsById("invalid-id");
    }
}
