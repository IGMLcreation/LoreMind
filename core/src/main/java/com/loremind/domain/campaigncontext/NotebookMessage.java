package com.loremind.domain.campaigncontext;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * Un message de la conversation d'un {@link Notebook}. {@code role} = "user" ou
 * "assistant". Persisté pour recharger l'historique de l'atelier.
 */
@Data
@Builder
public class NotebookMessage {
    private String id;
    private String notebookId;
    private String role;
    private String content;
    private LocalDateTime createdAt;
}
