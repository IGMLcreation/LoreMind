package com.loremind.domain.conversationcontext;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

/**
 * Un message persiste d'une conversation.
 *
 * Distinct de {@link com.loremind.domain.generationcontext.ChatMessage}
 * qui reste un simple record role+content pour le streaming LLM. Ici
 * on ajoute id et horodatage, necessaires pour l'affichage / l'ordre.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ConversationMessage {

    private String id;
    /** "user" | "assistant" | "system". */
    private String role;
    private String content;
    private LocalDateTime createdAt;
}
