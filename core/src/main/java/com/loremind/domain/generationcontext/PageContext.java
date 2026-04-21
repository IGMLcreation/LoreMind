package com.loremind.domain.generationcontext;

import lombok.Builder;
import lombok.Value;

import java.util.List;
import java.util.Map;

/**
 * Contexte d'une page spécifique en cours d'édition.
 * <p>
 * Complément du LoreStructuralContext : l'un donne la carte générale du
 * Lore, l'autre zoome sur la page précise en cours de discussion. Permet
 * à l'IA de focaliser ses suggestions sur les bons champs sans déborder
 * sur d'autres pages/templates.
 * <p>
 * Object de valeur immuable, pur domaine — aucune dépendance technique.
 */
@Value
@Builder
public class PageContext {

    String title;
    String templateName;
    List<String> templateFields;
    Map<String, String> values;
}
