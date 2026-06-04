package com.loremind.domain.gamesystemcontext;

import java.util.List;

/**
 * Évènement d'avancement émis pendant l'import streamé d'un PDF de règles.
 * <p>
 * {@code total} = nombre de morceaux à traiter (0 tant que l'extraction n'est
 * pas finie). {@code current} = morceaux déjà traités. {@code newSectionTitles}
 * = titres de sections nouvellement trouvés/complétés par le dernier morceau
 * (pour un affichage « au fil de l'eau »).
 */
public record RulesImportProgress(
        int current,
        int total,
        int pageCount,
        int ocrPageCount,
        List<String> newSectionTitles) {
}
