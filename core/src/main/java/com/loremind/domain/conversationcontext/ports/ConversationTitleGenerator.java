package com.loremind.domain.conversationcontext.ports;

import com.loremind.domain.conversationcontext.ConversationMessage;

import java.util.List;

/**
 * Port : generation d'un titre court a partir des premiers echanges d'une
 * conversation. Implemente via un appel Brain /summarize/conversation-title.
 */
public interface ConversationTitleGenerator {

    /**
     * Renvoie un titre court (4-7 mots max), dans la langue de l'utilisateur
     * (relayee au Brain via l'entete X-User-Language). Jamais null ni vide.
     */
    String generate(List<ConversationMessage> firstMessages);
}
