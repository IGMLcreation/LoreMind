package com.loremind.infrastructure.web.dto.generationcontext;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * DTO HTTP pour un message d'une conversation.
 * Rôles acceptés : "user", "assistant", "system".
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class ChatMessageDTO {

    private String role;
    private String content;
}
