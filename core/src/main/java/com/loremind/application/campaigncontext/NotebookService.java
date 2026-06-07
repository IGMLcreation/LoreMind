package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.Notebook;
import com.loremind.domain.campaigncontext.NotebookMessage;
import com.loremind.domain.campaigncontext.NotebookSource;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.NotebookIndexer;
import com.loremind.domain.campaigncontext.ports.NotebookRepository;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * Service d'application des notebooks (atelier RAG) : CRUD, indexation des sources
 * (déléguée au Brain), conversation persistée, et assemblage du contexte campagne.
 */
@Service
public class NotebookService {

    private final NotebookRepository repository;
    private final NotebookIndexer indexer;
    private final CampaignRepository campaignRepository;
    private final CampaignBriefBuilder briefBuilder;

    public NotebookService(
            NotebookRepository repository,
            NotebookIndexer indexer,
            CampaignRepository campaignRepository,
            CampaignBriefBuilder briefBuilder) {
        this.repository = repository;
        this.indexer = indexer;
        this.campaignRepository = campaignRepository;
        this.briefBuilder = briefBuilder;
    }

    // --- Notebooks ---

    public Notebook createNotebook(String campaignId, String name) {
        String safeName = (name == null || name.isBlank()) ? "Nouvel atelier" : name.trim();
        return repository.save(Notebook.builder().campaignId(campaignId).name(safeName).build());
    }

    public java.util.Optional<Notebook> getNotebook(String id) {
        return repository.findById(id);
    }

    public List<Notebook> getNotebooksByCampaign(String campaignId) {
        return repository.findByCampaignId(campaignId);
    }

    public Notebook renameNotebook(String id, String name) {
        Notebook nb = repository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Notebook introuvable: " + id));
        nb.setName((name == null || name.isBlank()) ? nb.getName() : name.trim());
        return repository.save(nb);
    }

    public void deleteNotebook(String id) {
        // Supprime les vecteurs de chaque source côté Brain (best-effort) avant la BDD.
        for (NotebookSource s : repository.findSourcesByNotebookId(id)) {
            indexer.delete(s.getId());
        }
        repository.deleteById(id);
    }

    // --- Sources ---

    public List<NotebookSource> getSources(String notebookId) {
        return repository.findSourcesByNotebookId(notebookId);
    }

    /**
     * Ajoute une source : crée la ligne (INDEXING), lance l'indexation Brain, puis
     * met à jour (READY + compteurs) ou (FAILED) en cas d'échec — et relaie l'erreur.
     */
    public NotebookSource addSource(String notebookId, String filename, byte[] pdfBytes) {
        if (!repository.existsById(notebookId)) {
            throw new IllegalArgumentException("Notebook introuvable: " + notebookId);
        }
        NotebookSource source = repository.saveSource(NotebookSource.builder()
                .notebookId(notebookId)
                .filename(filename != null && !filename.isBlank() ? filename : "source.pdf")
                .status("INDEXING")
                .build());
        try {
            NotebookIndexer.IndexResult result = indexer.index(source.getId(), pdfBytes, filename);
            source.setStatus("READY");
            source.setChunkCount(result.chunks());
            source.setPageCount(result.pageCount());
            return repository.saveSource(source);
        } catch (RuntimeException e) {
            source.setStatus("FAILED");
            repository.saveSource(source);
            throw e;
        }
    }

    public void deleteSource(String sourceId) {
        repository.findSourceById(sourceId).ifPresent(s -> indexer.delete(s.getId()));
        repository.deleteSourceById(sourceId);
    }

    public List<String> readySourceIds(String notebookId) {
        return repository.findSourcesByNotebookId(notebookId).stream()
                .filter(s -> "READY".equals(s.getStatus()))
                .map(NotebookSource::getId)
                .toList();
    }

    // --- Conversation ---

    public List<NotebookMessage> getMessages(String notebookId) {
        return repository.findMessagesByNotebookId(notebookId);
    }

    public NotebookMessage addMessage(String notebookId, String role, String content) {
        return repository.saveMessage(NotebookMessage.builder()
                .notebookId(notebookId).role(role).content(content).build());
    }

    // --- Contexte campagne (oriente l'IA) ---

    /** Brief COMPLET de la campagne (structure arcs/chapitres/scènes + PNJ + lore) :
     *  l'IA « voit » la campagne, pas seulement son nom. */
    public String buildContext(String campaignId) {
        if (campaignId == null) return "";
        Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null) return "";
        return briefBuilder.build(campaign);
    }
}
