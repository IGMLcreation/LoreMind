package com.loremind.domain.playcontext.ports;

/**
 * Port de sortie (mode séance) : génère le récapitulatif « précédemment… » d'une séance
 * à partir de son journal. Implémenté par un client du Brain. One-shot, texte libre.
 */
public interface SessionRecapAssistant {

    /**
     * Résume le journal d'une séance en un récap à lire aux joueurs à l'ouverture
     * de la séance suivante.
     *
     * @param transcript entrées du journal, chronologiques, une par ligne
     * @param context    méta courte (nom de la campagne / de la séance), peut être vide
     * @return le récap rédigé (jamais null ; peut être court si le journal est maigre)
     */
    String generateRecap(String transcript, String context);
}
