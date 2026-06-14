package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.campaigncontext.Notebook;
import com.loremind.domain.campaigncontext.NotebookMessage;
import com.loremind.domain.campaigncontext.NotebookSource;
import com.loremind.domain.campaigncontext.ports.NotebookRepository;
import com.loremind.infrastructure.persistence.entity.NotebookJpaEntity;
import com.loremind.infrastructure.persistence.entity.NotebookMessageJpaEntity;
import com.loremind.infrastructure.persistence.entity.NotebookSourceJpaEntity;
import com.loremind.infrastructure.persistence.jpa.NotebookJpaRepository;
import com.loremind.infrastructure.persistence.jpa.NotebookMessageJpaRepository;
import com.loremind.infrastructure.persistence.jpa.NotebookSourceJpaRepository;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;

@Repository
public class PostgresNotebookRepository implements NotebookRepository {

    private final NotebookJpaRepository notebookJpa;
    private final NotebookSourceJpaRepository sourceJpa;
    private final NotebookMessageJpaRepository messageJpa;

    public PostgresNotebookRepository(
            NotebookJpaRepository notebookJpa,
            NotebookSourceJpaRepository sourceJpa,
            NotebookMessageJpaRepository messageJpa) {
        this.notebookJpa = notebookJpa;
        this.sourceJpa = sourceJpa;
        this.messageJpa = messageJpa;
    }

    // --- Notebook ---

    @Override
    public Notebook save(Notebook notebook) {
        NotebookJpaEntity entity = notebook.getId() != null
                ? notebookJpa.findById(Long.parseLong(notebook.getId())).orElseGet(NotebookJpaEntity::new)
                : new NotebookJpaEntity();
        entity.setName(notebook.getName());
        entity.setCampaignId(Long.parseLong(notebook.getCampaignId()));
        return toNotebook(notebookJpa.save(entity));
    }

    @Override
    public Optional<Notebook> findById(String id) {
        return notebookJpa.findById(Long.parseLong(id)).map(this::toNotebook);
    }

    @Override
    public List<Notebook> findByCampaignId(String campaignId) {
        return notebookJpa.findByCampaignIdOrderByUpdatedAtDesc(Long.parseLong(campaignId)).stream()
                .map(this::toNotebook).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void deleteById(String id) {
        Long nid = Long.parseLong(id);
        messageJpa.deleteByNotebookId(nid);
        sourceJpa.deleteByNotebookId(nid);
        notebookJpa.deleteById(nid);
    }

    @Override
    public boolean existsById(String id) {
        return notebookJpa.existsById(Long.parseLong(id));
    }

    // --- Sources ---

    @Override
    public NotebookSource saveSource(NotebookSource source) {
        NotebookSourceJpaEntity entity = source.getId() != null
                ? sourceJpa.findById(Long.parseLong(source.getId())).orElseGet(NotebookSourceJpaEntity::new)
                : new NotebookSourceJpaEntity();
        entity.setNotebookId(Long.parseLong(source.getNotebookId()));
        entity.setFilename(source.getFilename());
        entity.setStatus(source.getStatus());
        entity.setChunkCount(source.getChunkCount());
        entity.setPageCount(source.getPageCount());
        return toSource(sourceJpa.save(entity));
    }

    @Override
    public Optional<NotebookSource> findSourceById(String id) {
        return sourceJpa.findById(Long.parseLong(id)).map(this::toSource);
    }

    @Override
    public List<NotebookSource> findSourcesByNotebookId(String notebookId) {
        return sourceJpa.findByNotebookIdOrderByCreatedAtAsc(Long.parseLong(notebookId)).stream()
                .map(this::toSource).collect(Collectors.toList());
    }

    @Override
    public void deleteSourceById(String id) {
        sourceJpa.deleteById(Long.parseLong(id));
    }

    // --- Messages ---

    @Override
    public NotebookMessage saveMessage(NotebookMessage message) {
        NotebookMessageJpaEntity entity = NotebookMessageJpaEntity.builder()
                .notebookId(Long.parseLong(message.getNotebookId()))
                .role(message.getRole())
                .content(message.getContent())
                .build();
        return toMessage(messageJpa.save(entity));
    }

    @Override
    public List<NotebookMessage> findMessagesByNotebookId(String notebookId) {
        return messageJpa.findByNotebookIdAndArchivedAtIsNullOrderByCreatedAtAsc(Long.parseLong(notebookId)).stream()
                .map(this::toMessage).collect(Collectors.toList());
    }

    @Override
    @Transactional
    public void archiveMessagesByNotebookId(String notebookId) {
        messageJpa.archiveActiveMessages(Long.parseLong(notebookId), java.time.LocalDateTime.now());
    }

    @Override
    public List<NotebookMessage> findArchivedMessagesByNotebookId(String notebookId) {
        return messageJpa.findByNotebookIdAndArchivedAtIsNotNullOrderByCreatedAtAsc(Long.parseLong(notebookId)).stream()
                .map(this::toMessage).collect(Collectors.toList());
    }

    // --- Mapping ---

    private Notebook toNotebook(NotebookJpaEntity e) {
        return Notebook.builder()
                .id(e.getId().toString())
                .name(e.getName())
                .campaignId(e.getCampaignId().toString())
                .createdAt(e.getCreatedAt())
                .updatedAt(e.getUpdatedAt())
                .build();
    }

    private NotebookSource toSource(NotebookSourceJpaEntity e) {
        return NotebookSource.builder()
                .id(e.getId().toString())
                .notebookId(e.getNotebookId().toString())
                .filename(e.getFilename())
                .status(e.getStatus())
                .chunkCount(e.getChunkCount())
                .pageCount(e.getPageCount())
                .createdAt(e.getCreatedAt())
                .build();
    }

    private NotebookMessage toMessage(NotebookMessageJpaEntity e) {
        return NotebookMessage.builder()
                .id(e.getId().toString())
                .notebookId(e.getNotebookId().toString())
                .role(e.getRole())
                .content(e.getContent())
                .createdAt(e.getCreatedAt())
                .archivedAt(e.getArchivedAt())
                .build();
    }
}
