package com.loremind.domain.campaigncontext;

import java.util.List;

/**
 * Proposition de PEUPLEMENT d'un chapitre en scènes (Pilier A — capacité « create »).
 * Éphémère : le front affiche les ébauches, l'utilisateur accepte/rejette, puis renvoie
 * ce record filtré aux ébauches retenues au endpoint d'application (qui crée les scènes).
 *
 * @param chapterId chapitre cible où créer les scènes
 * @param scenes    ébauches proposées / retenues
 */
public record SceneDraftProposal(String chapterId, List<SceneDraft> scenes) {
}
