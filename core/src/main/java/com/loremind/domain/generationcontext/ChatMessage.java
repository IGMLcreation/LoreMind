package com.loremind.domain.generationcontext;

/**
 * Un message d'une conversation avec le LLM.
 * <p>
 * Rôles acceptés : "user", "assistant", "system".
 * Object de valeur immuable — cohérent avec le reste du domaine.
 */
public record ChatMessage(String role, String content) {

}
