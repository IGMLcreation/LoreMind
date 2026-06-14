package com.loremind.domain.campaigncontext;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Atelier d'adaptation (« notebook ») d'une campagne : une ou plusieurs sources
 * PDF indexées (RAG) + une conversation, persistés pour y revenir.
 * <p>
 * Les SOURCES ({@link NotebookSource}) et les MESSAGES ({@link NotebookMessage})
 * sont gérés comme entités liées par {@code notebookId} (chargées séparément).
 */
@Data
@Builder
public class Notebook {
    private String id;
    private String name;
    private String campaignId;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
