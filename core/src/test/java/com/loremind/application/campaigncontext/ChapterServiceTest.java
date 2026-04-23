package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.Scene;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
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
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test unitaire pour ChapterService.
 * Utilise des mocks pour les ports de sortie (ChapterRepository).
 * Teste la logique d'orchestration de la couche Application.
 */
@ExtendWith(MockitoExtension.class)
public class ChapterServiceTest {

    @Mock
    private ChapterRepository chapterRepository;
    @Mock
    private SceneRepository sceneRepository;

    @InjectMocks
    private ChapterService chapterService;

    private Chapter testChapter;

    @BeforeEach
    void setUp() {
        testChapter = Chapter.builder()
                .id("chapter-1")
                .name("Test Chapter")
                .description("Test Description")
                .arcId("arc-1")
                .order(1)
                .build();
    }

    @Test
    void testCreateChapter() {
        // Arrange
        when(chapterRepository.save(any(Chapter.class))).thenReturn(testChapter);

        // Act
        Chapter result = chapterService.createChapter("New Chapter", "Description", "arc-1", 1);

        // Assert
        assertNotNull(result);
        verify(chapterRepository, times(1)).save(any(Chapter.class));
    }

    @Test
    void testGetChapterById_Found() {
        // Arrange
        when(chapterRepository.findById("chapter-1")).thenReturn(Optional.of(testChapter));

        // Act
        Optional<Chapter> result = chapterService.getChapterById("chapter-1");

        // Assert
        assertTrue(result.isPresent());
        assertEquals("Test Chapter", result.get().getName());
        verify(chapterRepository, times(1)).findById("chapter-1");
    }

    @Test
    void testGetChapterById_NotFound() {
        // Arrange
        when(chapterRepository.findById("invalid-id")).thenReturn(Optional.empty());

        // Act
        Optional<Chapter> result = chapterService.getChapterById("invalid-id");

        // Assert
        assertFalse(result.isPresent());
        verify(chapterRepository, times(1)).findById("invalid-id");
    }

    @Test
    void testGetAllChapters() {
        // Arrange
        Chapter chapter2 = Chapter.builder()
                .id("chapter-2")
                .name("Chapter 2")
                .build();
        when(chapterRepository.findAll()).thenReturn(List.of(testChapter, chapter2));

        // Act
        List<Chapter> result = chapterService.getAllChapters();

        // Assert
        assertEquals(2, result.size());
        verify(chapterRepository, times(1)).findAll();
    }

    @Test
    void testGetChaptersByArcId() {
        // Arrange
        when(chapterRepository.findByArcId("arc-1")).thenReturn(List.of(testChapter));

        // Act
        List<Chapter> result = chapterService.getChaptersByArcId("arc-1");

        // Assert
        assertEquals(1, result.size());
        verify(chapterRepository, times(1)).findByArcId("arc-1");
    }

    @Test
    void testUpdateChapter_Success() {
        // Arrange
        Chapter updatedChapter = Chapter.builder()
                .name("Updated Chapter")
                .description("Updated Description")
                .order(2)
                .gmNotes("GM notes")
                .playerObjectives("Objectives")
                .narrativeStakes("Stakes")
                .relatedPageIds(List.of("page-1"))
                .illustrationImageIds(List.of("image-1"))
                .build();
        when(chapterRepository.findById("chapter-1")).thenReturn(Optional.of(testChapter));
        when(chapterRepository.save(any(Chapter.class))).thenReturn(testChapter);

        // Act
        Chapter result = chapterService.updateChapter("chapter-1", updatedChapter);

        // Assert
        assertNotNull(result);
        verify(chapterRepository, times(1)).findById("chapter-1");
        verify(chapterRepository, times(1)).save(any(Chapter.class));
    }

    @Test
    void testUpdateChapter_NotFound() {
        // Arrange
        Chapter updatedChapter = Chapter.builder()
                .name("Updated Chapter")
                .build();
        when(chapterRepository.findById("invalid-id")).thenReturn(Optional.empty());

        // Act & Assert
        IllegalArgumentException exception = assertThrows(
                IllegalArgumentException.class,
                () -> chapterService.updateChapter("invalid-id", updatedChapter)
        );
        assertEquals("Chapter non trouvé avec l'ID: invalid-id", exception.getMessage());
        verify(chapterRepository, times(1)).findById("invalid-id");
        verify(chapterRepository, never()).save(any());
    }

    @Test
    void testDeleteChapter_EmptyChapter() {
        // Aucune scène : Mockito renvoie List.of() par défaut.
        chapterService.deleteChapter("chapter-1");

        verify(chapterRepository).deleteById("chapter-1");
        verify(sceneRepository, never()).deleteById(anyString());
    }

    @Test
    void testDeleteChapter_CascadesScenes() {
        Scene s1 = Scene.builder().id("s-1").chapterId("chapter-1").name("S1").build();
        Scene s2 = Scene.builder().id("s-2").chapterId("chapter-1").name("S2").build();
        when(sceneRepository.findByChapterId("chapter-1")).thenReturn(List.of(s1, s2));

        chapterService.deleteChapter("chapter-1");

        verify(sceneRepository).deleteById("s-1");
        verify(sceneRepository).deleteById("s-2");
        verify(chapterRepository).deleteById("chapter-1");
    }

    @Test
    void testGetDeletionImpact() {
        Scene s1 = Scene.builder().id("s-1").chapterId("chapter-1").name("S1").build();
        Scene s2 = Scene.builder().id("s-2").chapterId("chapter-1").name("S2").build();
        when(sceneRepository.findByChapterId("chapter-1")).thenReturn(List.of(s1, s2));

        ChapterService.DeletionImpact impact = chapterService.getDeletionImpact("chapter-1");

        assertEquals(2, impact.scenes());
    }

    @Test
    void testChapterExists_True() {
        // Arrange
        when(chapterRepository.existsById("chapter-1")).thenReturn(true);

        // Act
        boolean result = chapterService.chapterExists("chapter-1");

        // Assert
        assertTrue(result);
        verify(chapterRepository, times(1)).existsById("chapter-1");
    }

    @Test
    void testChapterExists_False() {
        // Arrange
        when(chapterRepository.existsById("invalid-id")).thenReturn(false);

        // Act
        boolean result = chapterService.chapterExists("invalid-id");

        // Assert
        assertFalse(result);
        verify(chapterRepository, times(1)).existsById("invalid-id");
    }
}
