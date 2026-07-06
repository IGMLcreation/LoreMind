package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.notebook.Notebook;
import com.loremind.domain.campaigncontext.notebook.NotebookMessage;
import com.loremind.domain.campaigncontext.notebook.NotebookSource;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.NotebookIndexer;
import com.loremind.domain.campaigncontext.ports.NotebookRepository;
import com.loremind.domain.gamesystemcontext.GameSystem;
import com.loremind.domain.gamesystemcontext.ports.GameSystemRepository;
import com.loremind.domain.shared.template.FieldType;
import com.loremind.domain.shared.template.TemplateField;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.*;

/**
 * Test unitaire pour NotebookService.
 * Mocks des ports (repository, indexer Brain, campagne, brief builder, système).
 */
@ExtendWith(MockitoExtension.class)
public class NotebookServiceTest {

    @Mock
    private NotebookRepository repository;
    @Mock
    private NotebookIndexer indexer;
    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private CampaignBriefBuilder briefBuilder;
    @Mock
    private GameSystemRepository gameSystemRepository;

    @InjectMocks
    private NotebookService service;

    // --- createNotebook ---

    @Test
    void testCreateNotebook_UsesProvidedName() {
        when(repository.save(any(Notebook.class))).thenAnswer(inv -> inv.getArgument(0));

        Notebook result = service.createNotebook("camp-1", "  Mon atelier  ");

        assertEquals("Mon atelier", result.getName()); // trim
        assertEquals("camp-1", result.getCampaignId());
    }

    @Test
    void testCreateNotebook_DefaultNameWhenBlank() {
        when(repository.save(any(Notebook.class))).thenAnswer(inv -> inv.getArgument(0));

        Notebook result = service.createNotebook("camp-1", "   ");

        assertEquals("Nouvel atelier", result.getName());
    }

    @Test
    void testCreateNotebook_DefaultNameWhenNull() {
        when(repository.save(any(Notebook.class))).thenAnswer(inv -> inv.getArgument(0));

        Notebook result = service.createNotebook("camp-1", null);

        assertEquals("Nouvel atelier", result.getName());
    }

    // --- renameNotebook ---

    @Test
    void testRenameNotebook_Success() {
        Notebook existing = Notebook.builder().id("nb-1").name("Old").build();
        when(repository.findById("nb-1")).thenReturn(Optional.of(existing));
        when(repository.save(any(Notebook.class))).thenAnswer(inv -> inv.getArgument(0));

        Notebook result = service.renameNotebook("nb-1", "  Nouveau  ");

        assertEquals("Nouveau", result.getName());
    }

    @Test
    void testRenameNotebook_BlankKeepsExistingName() {
        Notebook existing = Notebook.builder().id("nb-1").name("Old").build();
        when(repository.findById("nb-1")).thenReturn(Optional.of(existing));
        when(repository.save(any(Notebook.class))).thenAnswer(inv -> inv.getArgument(0));

        Notebook result = service.renameNotebook("nb-1", "   ");

        assertEquals("Old", result.getName());
    }

    @Test
    void testRenameNotebook_NotFound() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.renameNotebook("missing", "X"));
        assertEquals("Notebook introuvable: missing", ex.getMessage());
    }

    // --- deleteNotebook ---

    @Test
    void testDeleteNotebook_DeletesVectorsThenRow() {
        when(repository.findSourcesByNotebookId("nb-1")).thenReturn(List.of(
                NotebookSource.builder().id("src-1").build(),
                NotebookSource.builder().id("src-2").build()));

        service.deleteNotebook("nb-1");

        verify(indexer).delete("src-1");
        verify(indexer).delete("src-2");
        verify(repository).deleteById("nb-1");
    }

    // --- addSource ---

    @Test
    void testAddSource_Success_SetsReadyAndCounters() {
        when(repository.existsById("nb-1")).thenReturn(true);
        when(repository.saveSource(any(NotebookSource.class))).thenAnswer(inv -> {
            NotebookSource s = inv.getArgument(0);
            if (s.getId() == null) s.setId("src-1");
            return s;
        });
        when(indexer.index(eq("src-1"), any(), eq("doc.pdf")))
                .thenReturn(new NotebookIndexer.IndexResult(42, 10, 2));

        NotebookSource result = service.addSource("nb-1", "doc.pdf", new byte[]{1, 2, 3});

        assertEquals("READY", result.getStatus());
        assertEquals(42, result.getChunkCount());
        assertEquals(10, result.getPageCount());
        // saveSource appelé 2 fois : création INDEXING + mise à jour READY.
        verify(repository, times(2)).saveSource(any(NotebookSource.class));
    }

    @Test
    void testAddSource_DefaultFilenameWhenBlank() {
        when(repository.existsById("nb-1")).thenReturn(true);
        when(repository.saveSource(any(NotebookSource.class))).thenAnswer(inv -> {
            NotebookSource s = inv.getArgument(0);
            if (s.getId() == null) s.setId("src-1");
            return s;
        });
        when(indexer.index(anyString(), any(), any()))
                .thenReturn(new NotebookIndexer.IndexResult(1, 1, 0));

        service.addSource("nb-1", "  ", new byte[]{1});

        ArgumentCaptor<NotebookSource> captor = ArgumentCaptor.forClass(NotebookSource.class);
        verify(repository, atLeastOnce()).saveSource(captor.capture());
        assertEquals("source.pdf", captor.getAllValues().get(0).getFilename());
    }

    @Test
    void testAddSource_NotebookNotFound() {
        when(repository.existsById("missing")).thenReturn(false);

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.addSource("missing", "doc.pdf", new byte[]{1}));
        assertEquals("Notebook introuvable: missing", ex.getMessage());
        verify(repository, never()).saveSource(any());
    }

    @Test
    void testAddSource_IndexingFailure_SetsFailedAndRethrows() {
        when(repository.existsById("nb-1")).thenReturn(true);
        when(repository.saveSource(any(NotebookSource.class))).thenAnswer(inv -> {
            NotebookSource s = inv.getArgument(0);
            if (s.getId() == null) s.setId("src-1");
            return s;
        });
        when(indexer.index(anyString(), any(), any()))
                .thenThrow(new RuntimeException("Brain KO"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.addSource("nb-1", "doc.pdf", new byte[]{1}));
        assertEquals("Brain KO", ex.getMessage());

        ArgumentCaptor<NotebookSource> captor = ArgumentCaptor.forClass(NotebookSource.class);
        verify(repository, times(2)).saveSource(captor.capture());
        assertEquals("FAILED", captor.getAllValues().get(1).getStatus());
    }

    // --- deleteSource ---

    @Test
    void testDeleteSource_DeletesVectorsThenRow() {
        when(repository.findSourceById("src-1"))
                .thenReturn(Optional.of(NotebookSource.builder().id("src-1").build()));

        service.deleteSource("src-1");

        verify(indexer).delete("src-1");
        verify(repository).deleteSourceById("src-1");
    }

    @Test
    void testDeleteSource_UnknownSource_StillDeletesRow() {
        when(repository.findSourceById("src-x")).thenReturn(Optional.empty());

        service.deleteSource("src-x");

        verify(indexer, never()).delete(anyString());
        verify(repository).deleteSourceById("src-x");
    }

    // --- readySourceIds ---

    @Test
    void testReadySourceIds_FiltersOnReadyStatus() {
        when(repository.findSourcesByNotebookId("nb-1")).thenReturn(List.of(
                NotebookSource.builder().id("a").status("READY").build(),
                NotebookSource.builder().id("b").status("INDEXING").build(),
                NotebookSource.builder().id("c").status("READY").build(),
                NotebookSource.builder().id("d").status("FAILED").build()));

        List<String> result = service.readySourceIds("nb-1");

        assertEquals(List.of("a", "c"), result);
    }

    // --- messages ---

    @Test
    void testAddMessage_DelegatesToRepo() {
        when(repository.saveMessage(any(NotebookMessage.class))).thenAnswer(inv -> inv.getArgument(0));

        NotebookMessage result = service.addMessage("nb-1", "user", "salut");

        assertEquals("nb-1", result.getNotebookId());
        assertEquals("user", result.getRole());
        assertEquals("salut", result.getContent());
    }

    @Test
    void testClearChat_Archives() {
        service.clearChat("nb-1");
        verify(repository).archiveMessagesByNotebookId("nb-1");
    }

    // --- buildArchiveContext ---

    @Test
    void testBuildArchiveContext_EmptyKeysReturnsEmpty() {
        assertEquals("", service.buildArchiveContext("nb-1", null));
        assertEquals("", service.buildArchiveContext("nb-1", List.of()));
        verify(repository, never()).findArchivedMessagesByNotebookId(anyString());
    }

    @Test
    void testBuildArchiveContext_NoMatchingKeysReturnsEmpty() {
        LocalDateTime at = LocalDateTime.of(2026, 1, 1, 10, 0);
        when(repository.findArchivedMessagesByNotebookId("nb-1")).thenReturn(List.of(
                NotebookMessage.builder().role("user").content("hi").archivedAt(at).build()));

        String result = service.buildArchiveContext("nb-1", List.of("2099-12-31T00:00"));

        assertEquals("", result);
    }

    @Test
    void testBuildArchiveContext_FormatsSelectedArchive() {
        LocalDateTime at = LocalDateTime.of(2026, 1, 1, 10, 0);
        when(repository.findArchivedMessagesByNotebookId("nb-1")).thenReturn(List.of(
                NotebookMessage.builder().role("user").content("Question ?").archivedAt(at).build(),
                NotebookMessage.builder().role("assistant").content("Réponse.").archivedAt(at).build()));

        String result = service.buildArchiveContext("nb-1", List.of(at.toString()));

        assertTrue(result.contains("ANCIENNES CONVERSATIONS"));
        assertTrue(result.contains("[Archive du " + at + "]"));
        assertTrue(result.contains("MJ : Question ?"));
        assertTrue(result.contains("IA : Réponse."));
        assertTrue(result.contains("FIN DES ANCIENNES CONVERSATIONS"));
    }

    @Test
    void testBuildArchiveContext_TruncatesFromStartWhenTooLong() {
        LocalDateTime at = LocalDateTime.of(2026, 1, 1, 10, 0);
        // Budget pour 1 seule archive = max(2000, 16000/1) = 16000 chars. Au-delà,
        // la troncature se fait PAR LE DÉBUT : la fin (conclusion) doit survivre.
        String head = "A".repeat(17000);
        String tail = "CONCLUSION_UTILE";
        when(repository.findArchivedMessagesByNotebookId("nb-1")).thenReturn(List.of(
                NotebookMessage.builder().role("assistant").content(head + tail).archivedAt(at).build()));

        String result = service.buildArchiveContext("nb-1", List.of(at.toString()));

        assertTrue(result.contains("[…début tronqué…]"));
        assertTrue(result.contains(tail));
    }

    // --- buildContext (campagne) ---

    @Test
    void testBuildContext_NullCampaignIdReturnsEmpty() {
        assertEquals("", service.buildContext(null));
        verify(briefBuilder, never()).build(any());
    }

    @Test
    void testBuildContext_UnknownCampaignReturnsEmpty() {
        when(campaignRepository.findById("camp-x")).thenReturn(Optional.empty());

        assertEquals("", service.buildContext("camp-x"));
        verify(briefBuilder, never()).build(any());
    }

    @Test
    void testBuildContext_BriefOnlyWhenNoGameSystem() {
        Campaign campaign = Campaign.builder().id("camp-1").name("Camp").build();
        when(campaignRepository.findById("camp-1")).thenReturn(Optional.of(campaign));
        when(briefBuilder.build(campaign)).thenReturn("BRIEF");

        String result = service.buildContext("camp-1");

        assertEquals("BRIEF", result);
    }

    @Test
    void testBuildContext_AppendsNpcTextFields() {
        Campaign campaign = Campaign.builder().id("camp-1").name("Camp").gameSystemId("gs-1").build();
        when(campaignRepository.findById("camp-1")).thenReturn(Optional.of(campaign));
        when(briefBuilder.build(campaign)).thenReturn("BRIEF");

        GameSystem gs = GameSystem.builder()
                .id("gs-1")
                .npcTemplate(List.of(
                        new TemplateField("Histoire", FieldType.TEXT),
                        new TemplateField("Apparence", FieldType.TEXT),
                        new TemplateField("Portrait", FieldType.IMAGE)))  // non-TEXT -> exclu
                .build();
        when(gameSystemRepository.findById("gs-1")).thenReturn(Optional.of(gs));

        String result = service.buildContext("camp-1");

        assertTrue(result.startsWith("BRIEF\n\n"));
        assertTrue(result.contains("FICHE PNJ"));
        assertTrue(result.contains("Histoire"));
        assertTrue(result.contains("Apparence"));
        assertFalse(result.contains("Portrait"));
    }

    @Test
    void testBuildContext_NoNpcTemplateReturnsBriefOnly() {
        Campaign campaign = Campaign.builder().id("camp-1").name("Camp").gameSystemId("gs-1").build();
        when(campaignRepository.findById("camp-1")).thenReturn(Optional.of(campaign));
        when(briefBuilder.build(campaign)).thenReturn("BRIEF");
        when(gameSystemRepository.findById("gs-1"))
                .thenReturn(Optional.of(GameSystem.builder().id("gs-1").npcTemplate(null).build()));

        String result = service.buildContext("camp-1");

        assertEquals("BRIEF", result);
    }
}
