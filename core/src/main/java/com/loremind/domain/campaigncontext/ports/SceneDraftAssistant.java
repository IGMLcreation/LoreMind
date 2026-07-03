package com.loremind.domain.campaigncontext.ports;

import com.loremind.domain.campaigncontext.SceneDraft;

import java.util.List;

/**
 * Port de sortie (Pilier A — capacité « create ») : demande à l'IA d'ÉBAUCHER des scènes
 * pour un chapitre. Implémenté par un client du Brain. One-shot (pas de streaming).
 */
public interface SceneDraftAssistant {

    /**
     * Génère des ébauches de scènes cohérentes avec le contexte fourni.
     *
     * @param context     contexte narratif compact (chapitre + campagne + scènes existantes)
     * @param instruction consigne libre optionnelle du MJ (peut être vide/nulle)
     * @param count       nombre de scènes souhaité (indicatif)
     * @return ébauches proposées (titres non vides) ; vide si rien de pertinent
     */
    List<SceneDraft> draftScenes(String context, String instruction, int count);
}
