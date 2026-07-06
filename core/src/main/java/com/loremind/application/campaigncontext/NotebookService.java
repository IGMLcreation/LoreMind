package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.notebook.Notebook;
import com.loremind.domain.campaigncontext.notebook.NotebookMessage;
import com.loremind.domain.campaigncontext.notebook.NotebookSource;
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
    private final com.loremind.domain.gamesystemcontext.ports.GameSystemRepository gameSystemRepository;

    public NotebookService(
            NotebookRepository repository,
            NotebookIndexer indexer,
            CampaignRepository campaignRepository,
            CampaignBriefBuilder briefBuilder,
            com.loremind.domain.gamesystemcontext.ports.GameSystemRepository gameSystemRepository) {
        this.repository = repository;
        this.indexer = indexer;
        this.campaignRepository = campaignRepository;
        this.briefBuilder = briefBuilder;
        this.gameSystemRepository = gameSystemRepository;
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

    /** « Vider la conversation » : archive le fil actif (rien n'est supprimé). */
    public void clearChat(String notebookId) {
        repository.archiveMessagesByNotebookId(notebookId);
    }

    /** Messages archivés, chronologiques — l'appelant regroupe par {@code archivedAt}. */
    public List<NotebookMessage> getArchivedMessages(String notebookId) {
        return repository.findArchivedMessagesByNotebookId(notebookId);
    }

    // Budget total (caractères ≈ tokens/4) des archives injectées en référence :
    // borne le prompt même si l'utilisateur coche plusieurs longues conversations.
    private static final int ARCHIVE_CONTEXT_MAX_CHARS = 16000;

    /**
     * Bloc de contexte construit à partir des archives COCHÉES par l'utilisateur
     * (clés = {@code archivedAt.toString()}). Injecté dans le prompt du chat pour
     * que l'IA puisse s'appuyer sur d'anciennes conversations. Chaîne vide si
     * aucune clé valide. Chaque archive est tronquée PAR LE DÉBUT au-delà de son
     * budget : la fin d'une conversation (conclusions) est la partie utile.
     */
    public String buildArchiveContext(String notebookId, List<String> archivedAtKeys) {
        if (archivedAtKeys == null || archivedAtKeys.isEmpty()) return "";
        var wanted = new java.util.HashSet<>(archivedAtKeys);
        var groups = new java.util.LinkedHashMap<java.time.LocalDateTime, List<NotebookMessage>>();
        for (NotebookMessage m : repository.findArchivedMessagesByNotebookId(notebookId)) {
            if (m.getArchivedAt() != null && wanted.contains(m.getArchivedAt().toString())) {
                groups.computeIfAbsent(m.getArchivedAt(), k -> new java.util.ArrayList<>()).add(m);
            }
        }
        if (groups.isEmpty()) return "";

        int budgetPerArchive = Math.max(2000, ARCHIVE_CONTEXT_MAX_CHARS / groups.size());
        StringBuilder out = new StringBuilder(
                "--- ANCIENNES CONVERSATIONS DE CET ATELIER (références choisies par le MJ : "
                        + "tu peux t'appuyer sur leurs conclusions) ---\n");
        groups.forEach((archivedAt, messages) -> {
            StringBuilder convo = new StringBuilder();
            for (NotebookMessage m : messages) {
                convo.append("user".equals(m.getRole()) ? "MJ : " : "IA : ")
                        .append(m.getContent()).append('\n');
            }
            String text = convo.toString();
            if (text.length() > budgetPerArchive) {
                text = "[…début tronqué…]\n" + text.substring(text.length() - budgetPerArchive);
            }
            out.append("[Archive du ").append(archivedAt).append("]\n").append(text).append('\n');
        });
        out.append("--- FIN DES ANCIENNES CONVERSATIONS ---");
        return out.toString();
    }

    // --- Contexte campagne (oriente l'IA) ---

    /** Brief COMPLET de la campagne (structure arcs/chapitres/scènes + PNJ + lore) :
     *  l'IA « voit » la campagne, pas seulement son nom. */
    public String buildContext(String campaignId) {
        if (campaignId == null) return "";
        Campaign campaign = campaignRepository.findById(campaignId).orElse(null);
        if (campaign == null) return "";
        String brief = briefBuilder.build(campaign);
        // Champs TEXT de la fiche PNJ du système de jeu : permet à l'IA de remplir
        // `values` des actions "npc" avec les BONS noms de champs (Histoire,
        // Apparence…) au lieu de tout entasser dans une description générique.
        String npcFields = npcSheetFields(campaign.getGameSystemId());
        return npcFields.isEmpty() ? brief : brief + "\n\n" + npcFields;
    }

    private String npcSheetFields(String gameSystemId) {
        if (gameSystemId == null || gameSystemId.isBlank()) return "";
        var gameSystem = gameSystemRepository.findById(gameSystemId).orElse(null);
        if (gameSystem == null || gameSystem.getNpcTemplate() == null) return "";
        var names = gameSystem.getNpcTemplate().stream()
                .filter(f -> f.getType() == com.loremind.domain.shared.template.FieldType.TEXT)
                .map(com.loremind.domain.shared.template.TemplateField::getName)
                .filter(n -> n != null && !n.isBlank())
                .toList();
        if (names.isEmpty()) return "";
        return "FICHE PNJ — champs texte disponibles (clés à utiliser dans `values` "
                + "d'une action npc) : " + String.join(", ", names);
    }
}
