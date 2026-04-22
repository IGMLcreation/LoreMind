package com.loremind.domain.generationcontext;

import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotEquals;

/**
 * Tests unitaires pour le record ChatMessage.
 * Role est une chaine libre cote domaine (les roles acceptes "user"/"assistant"/
 * "system" sont une convention — le domaine ne l'impose pas, c'est la
 * couche application qui valide au besoin).
 */
class ChatMessageTest {

    @Test
    void accessors_exposeRoleAndContent() {
        ChatMessage msg = new ChatMessage("user", "Que se passe-t-il dans la taverne ?");
        assertEquals("user", msg.role());
        assertEquals("Que se passe-t-il dans la taverne ?", msg.content());
    }

    @Test
    void twoMessages_withSameContent_areEqual() {
        ChatMessage a = new ChatMessage("assistant", "Il y a 3 clients au bar.");
        ChatMessage b = new ChatMessage("assistant", "Il y a 3 clients au bar.");
        assertEquals(a, b);
    }

    @Test
    void twoMessages_differingOnRole_areNotEqual() {
        ChatMessage a = new ChatMessage("user", "hello");
        ChatMessage b = new ChatMessage("assistant", "hello");
        assertNotEquals(a, b);
    }
}
