package com.loremind.domain.generationcontext;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Contexte d'une entité narrative précise en cours d'édition (Arc, Chapter, ou Scene).
 * <p>
 * Ceci est un Value Object du Generation Context.
 * Équivalent de PageContext côté Lore mais appliqué à la Campagne : injecté
 * dans le system prompt pour orienter l'IA vers CETTE entité précise plutôt
 * que vers l'arbre narratif global. Modèle uniforme pour les 3 types :
 * un discriminator `entityType` + un titre + une map de champs textuels.
 * <p>
 * `fields` associe le nom d'un champ (ex: "themes", "playerNarration")
 * à sa valeur actuelle (chaîne vide si non renseigné). Utiliser une
 * LinkedHashMap à la construction pour un prompt lisible (ordre préservé).
 */
@Value
@Builder
public class NarrativeEntityContext {

    /** "arc", "chapter" ou "scene" — utilisé pour libeller le bloc du prompt. */
    String entityType;
    String title;
    Map<String, String> fields;
}
