package com.loremind.infrastructure.web.dto.gamesystemcontext;

import java.util.Map;

/**
 * Réponse de l'import d'un PDF de règles : proposition de sections à réviser.
 *
 * @param sections     titre de section → contenu markdown (non persisté).
 * @param pageCount     nombre de pages extraites du PDF.
 * @param ocrPageCount  nombre de pages ayant nécessité l'OCR (0 = born-digital).
 */
public record RulesImportResponseDTO(
        Map<String, String> sections,
        int pageCount,
        int ocrPageCount) {
}
