package com.loremind.domain.campaigncontext.ports;

import com.loremind.domain.campaigncontext.notebook.Notebook;
import com.loremind.domain.campaigncontext.notebook.NotebookMessage;
import com.loremind.domain.campaigncontext.notebook.NotebookSource;

import java.util.List;
import java.util.Optional;

/**
 * Port de sortie pour la persistance des notebooks (atelier), de leurs sources
 * et de leur conversation. Port unique (3 agrégats liés) pour rester compact.
 */
public interface NotebookRepository {

    // --- Notebook ---
    Notebook save(Notebook notebook);
    Optional<Notebook> findById(String id);
    List<Notebook> findByCampaignId(String campaignId);
    void deleteById(String id);
    boolean existsById(String id);

    // --- Sources ---
    NotebookSource saveSource(NotebookSource source);
    Optional<NotebookSource> findSourceById(String id);
    List<NotebookSource> findSourcesByNotebookId(String notebookId);
    void deleteSourceById(String id);

    // --- Messages (conversation) ---
    NotebookMessage saveMessage(NotebookMessage message);
    /** Messages de la conversation ACTIVE (les archives sont exclues). */
    List<NotebookMessage> findMessagesByNotebookId(String notebookId);
    /** « Vider » : archive le fil actif en un lot horodaté (rien n'est supprimé). */
    void archiveMessagesByNotebookId(String notebookId);
    /** Messages archivés, chronologiques (regroupables par {@code archivedAt}). */
    List<NotebookMessage> findArchivedMessagesByNotebookId(String notebookId);
}
