package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Prerequisite;
import com.loremind.domain.campaigncontext.Quest;
import com.loremind.domain.campaigncontext.ports.QuestRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.TreeSet;

/**
 * Service applicatif : énumère les noms de faits ({@link Prerequisite.FlagSet})
 * référencés par les quêtes d'une Campagne.
 *
 * <p>Modèle "déclaration implicite" : il n'existe pas de table de faits déclarés
 * globalement. Un fait existe dès qu'au moins une quête le référence dans ses
 * prérequis. Ce service expose la liste dédupliquée pour les UIs (toggle dans
 * la Partie, autocomplete dans l'éditeur de prérequis de quête).</p>
 *
 * <p>Niveau 1 : lit désormais les quêtes (entité de première classe), plus les
 * chapitres HUB.</p>
 */
@Service
public class CampaignReferencedFlagsService {

    private final QuestRepository questRepository;

    public CampaignReferencedFlagsService(QuestRepository questRepository) {
        this.questRepository = questRepository;
    }

    /** Retourne la liste triée alphabétiquement des noms de faits référencés. */
    public List<String> listForCampaign(String campaignId) {
        TreeSet<String> unique = new TreeSet<>();
        for (Quest quest : questRepository.findByCampaignId(campaignId)) {
            if (quest.getPrerequisites() == null) continue;
            for (Prerequisite p : quest.getPrerequisites()) {
                if (p instanceof Prerequisite.FlagSet f && f.flagName() != null && !f.flagName().isBlank()) {
                    unique.add(f.flagName());
                }
            }
        }
        return List.copyOf(unique);
    }
}
