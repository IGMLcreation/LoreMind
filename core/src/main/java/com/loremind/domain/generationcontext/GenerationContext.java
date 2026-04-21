package com.loremind.domain.generationcontext;

import lombok.Builder;
import lombok.Value;

import java.util.List;

/**
 * Object de valeur (immuable) représentant une demande de génération IA
 * pour remplir une Page à partir d'un Template.
 * <p>
 * Équivalent Java du PageGenerationContext Python (brain/app/domain/models.py).
 * Entité pure du domaine : aucune dépendance technique.
 * <p>
 * Immuable via @Value (Lombok) : pas de setters, tous les champs final.
 * C'est un DTO de domaine entrant dans le port AiProvider.
 */
@Value
@Builder
public class GenerationContext {

    String loreName;
    String loreDescription;
    String folderName;            // Nom du LoreNode parent (ex: "PNJ", "Lieux")
    String templateName;
    List<String> templateFields;  // Champs à générer (clés attendues dans la réponse)
    String pageTitle;
}
