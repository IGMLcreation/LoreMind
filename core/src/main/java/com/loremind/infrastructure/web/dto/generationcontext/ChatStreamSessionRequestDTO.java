package com.loremind.infrastructure.web.dto.generationcontext;

import lombok.Data;

import java.util.List;

/**
 * DTO de requête pour le chat IA d'une Session de jeu.
 * Le contexte (lore, campagne, gamesystem, journal) est dérivé du sessionId
 * côté serveur — l'appelant n'a qu'à fournir l'id et les messages.
 */
@Data
public class ChatStreamSessionRequestDTO {
    private String sessionId;
    private List<ChatMessageDTO> messages;
}
