package com.loremind.infrastructure.web.dto.generationcontext;

import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * DTO HTTP de requête pour POST /api/ai/chat/stream.
 * <p>
 * Le Core charge lui-même le Structural Context à partir de {loreId}.
 * Le frontend envoie uniquement l'historique de la conversation éphémère.
 */
@Data
@NoArgsConstructor
public class ChatStreamRequestDTO {

    private String loreId;
    /**
     * Optionnel : si fourni, l'IA reçoit aussi un PageContext focalisé sur
     * cette page précise (titre, template, champs, valeurs actuelles).
     * Sans pageId, le chat reste générique au Lore.
     */
    private String pageId;
    private List<ChatMessageDTO> messages;
}
