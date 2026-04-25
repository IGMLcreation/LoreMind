package com.loremind.domain.generationcontext;

import java.util.List;

/**
 * Object de valeur (immuable) représentant une demande de génération IA
 * pour remplir une Page à partir d'un Template.
 * <p>
 * Équivalent Java du PageGenerationContext Python (brain/app/domain/models.py).
 * Record Java : pur domaine, aucune dépendance technique.
 *
 * @param templateFields Champs à générer (clés attendues dans la réponse).
 * @param folderName     Nom du LoreNode parent (ex: "PNJ", "Lieux").
 */
public record GenerationContext(
        String loreName,
        String loreDescription,
        String folderName,
        String templateName,
        List<String> templateFields,
        String pageTitle) {
}
