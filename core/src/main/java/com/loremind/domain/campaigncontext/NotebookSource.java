package com.loremind.domain.campaigncontext;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Une source (PDF) d'un {@link Notebook}. Son {@code id} sert de clé d'indexation
 * vectorielle côté Brain (les vecteurs vivent sur le volume du Brain).
 * <p>
 * {@code status} : INDEXING (en cours), READY (interrogeable), FAILED (échec).
 */
@Data
@Builder
public class NotebookSource {
    private String id;
    private String notebookId;
    private String filename;
    private String status;
    private int chunkCount;
    private int pageCount;
    private LocalDateTime createdAt;
}
