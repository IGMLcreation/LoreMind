package com.loremind.domain.conversationcontext;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Agregat d'une conversation de chat IA persistee.
 *
 * Une conversation est ancree sur exactement un niveau de contexte :
 *  - un Lore (optionnellement une page precise)
 *  - une Campagne (optionnellement une entite narrative : arc/chapitre/scene)
 *
 * C'est cet ancrage qui permet au drawer de filtrer les conversations
 * a afficher dans la sidebar selon l'ecran en cours.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Conversation {

    private String id;
    private String title;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;

    /** Un seul des deux est non-null. */
    private String loreId;
    private String campaignId;

    /**
     * Type d'entite focus, null si la conversation est ancree au niveau
     * Lore/Campagne racine (pas sur une page/scene precise).
     * Valeurs : "page", "arc", "chapter", "scene".
     */
    private String entityType;
    private String entityId;

    @Builder.Default
    private List<ConversationMessage> messages = new ArrayList<>();
}
