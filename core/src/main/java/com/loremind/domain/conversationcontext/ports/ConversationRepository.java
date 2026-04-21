package com.loremind.domain.conversationcontext.ports;

import com.loremind.domain.conversationcontext.Conversation;
import com.loremind.domain.conversationcontext.ConversationMessage;

import java.util.List;
import java.util.Optional;

/**
 * Port de persistance des conversations de chat IA.
 *
 * Les methodes de lecture par contexte acceptent des filtres nullables :
 *  - `loreId` OU `campaignId` doit etre non-null (mais pas les deux)
 *  - `entityType` + `entityId` : soit tous les deux null (niveau racine),
 *    soit tous les deux non-null (niveau entite precise).
 */
public interface ConversationRepository {

    Conversation save(Conversation conversation);

    Optional<Conversation> findById(String id);

    /**
     * Liste les conversations filtrees par contexte strict, triees par
     * updatedAt desc. Les messages ne sont PAS chargees (liste vide) pour
     * garder la payload legere — la sidebar n'affiche que les titres.
     */
    List<Conversation> findByContext(
            String loreId,
            String campaignId,
            String entityType,
            String entityId);

    void deleteById(String id);

    /**
     * Ajoute un message a une conversation existante. Met a jour updatedAt
     * de la conversation parent. Renvoie le message persiste (avec id + ts).
     */
    ConversationMessage appendMessage(String conversationId, ConversationMessage message);

    /** Rename atomique — ne touche pas aux messages. */
    void updateTitle(String conversationId, String title);
}
