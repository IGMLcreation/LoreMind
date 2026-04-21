package com.loremind.infrastructure.web.dto.generationcontext;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * DTO exposé au frontend pour la génération IA d'une Page.
 * Format : { "values": { "fieldName1": "suggestion1", ... } }
 * <p>
 * On renvoie la map telle quelle — pas de tri, pas de filtrage côté serveur.
 * L'UI décide comment merger avec les valeurs déjà saisies par l'utilisateur.
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class GenerationSuggestionsDTO {

    private Map<String, String> values;
}
