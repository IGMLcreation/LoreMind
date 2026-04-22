package com.loremind.domain.generationcontext;

import lombok.Builder;
import lombok.Value;

import java.util.Map;

/**
 * Value Object représentant les règles de JDR injectées dans un prompt IA.
 * <p>
 * Contient uniquement les sections pertinentes pour l'intent de génération
 * en cours (sélection effectuée par GameSystemContextBuilder). Les sections
 * sont indexées par leur titre H2 original (ex : "Combat", "Classes").
 */
@Value
@Builder
public class GameSystemContext {

    /** Nom du système de JDR (ex : "Nimble", "D&D 5.1 SRD"). */
    String systemName;

    /** Description courte du système (nullable). */
    String systemDescription;

    /**
     * Sections de règles pertinentes, indexées par titre H2.
     * Vide si le GameSystem n'a aucune règle ou si aucune section ne matche l'intent.
     */
    Map<String, String> sections;
}
