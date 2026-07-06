package com.loremind.domain.campaigncontext.ports;

import com.loremind.domain.campaigncontext.randomtable.RandomTableEntry;

import java.util.List;

/**
 * Port de sortie : génération IA d'une table aléatoire et improvisation narrative
 * sur un résultat tiré. Implémenté par un client du Brain (service IA Python).
 */
public interface RandomTableGenerator {

    /** Table proposée (non persistée) à partir d'une description + formule de dé. */
    record GeneratedTable(String name, String description, List<RandomTableEntry> entries) {}

    /**
     * Génère une proposition de table couvrant la formule de dé, sur le sujet
     * donné, en s'appuyant sur le contexte (campagne, système…) s'il est fourni.
     */
    GeneratedTable generate(String description, String diceFormula, String context);

    /**
     * Brode un court récit (2-3 phrases) sur un résultat tiré, pour lancer la scène.
     */
    String improvise(String tableName, String resultLabel, String resultDetail, String context);
}
