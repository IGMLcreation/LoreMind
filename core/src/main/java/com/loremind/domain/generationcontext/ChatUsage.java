package com.loremind.domain.generationcontext;

/**
 * Instantané d'occupation de la fenêtre de contexte à l'instant t du chat.
 * <p>
 * Émis une fois par tour de chat (juste avant le streaming des tokens) pour
 * alimenter la jauge de contexte côté frontend. Les unités sont des tokens
 * (approximés via tiktoken côté Brain — ±10% vs le tokenizer réel du modèle).
 *
 * @param system  tokens consommés par le system prompt (contextes Lore/campagne injectés)
 * @param history tokens consommés par l'historique de la conversation (hors dernier message)
 * @param current tokens du dernier message utilisateur en attente de réponse
 * @param max     taille maximale configurée de la fenêtre de contexte
 */
public record ChatUsage(int system, int history, int current, int max) {
}
