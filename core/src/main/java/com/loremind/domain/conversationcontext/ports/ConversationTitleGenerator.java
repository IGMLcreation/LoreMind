package com.loremind.domain.conversationcontext.ports;

import com.loremind.domain.conversationcontext.ConversationMessage;

import java.util.List;

/**
 * Port : generation d'un titre court a partir des premiers echanges d'une
 * conversation. Implemente via un appel Brain /summarize/conversation-title.
 */
public interface ConversationTitleGenerator {

    /** Renvoie un titre en francais (4-7 mots max). Jamais null ni vide. */
    String generate(List<ConversationMessage> firstMessages);
}
