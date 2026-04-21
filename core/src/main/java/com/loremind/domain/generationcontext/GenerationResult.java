package com.loremind.domain.generationcontext;

import java.util.Map;

/**
 * Résultat d'une génération IA : une map fieldName -> valeur générée.
 * <p>
 * Équivalent Java du PageGenerationResult Python.
 * Immuable : une fois reçu, pas de modification (l'UI pourra faire du merge,
 * mais pas en mutant cet objet).
 */
public record GenerationResult(Map<String, String> values) {

}
