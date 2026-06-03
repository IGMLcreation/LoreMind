package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.Prerequisite;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.TreeSet;

/**
 * Service applicatif : énumère les noms de faits ({@link Prerequisite.FlagSet})
 * référencés par les chapitres d'une Campagne.
 *
 * <p>Modèle "déclaration implicite" : il n'existe pas de table de faits déclarés
 * globalement. Un fait existe dès qu'au moins une quête le référence dans ses
 * prérequis. Ce service expose la liste dédupliquée pour les UIs (toggle dans
 * la Partie, autocomplete dans l'éditeur de prérequis).</p>
 */
@Service
public class CampaignReferencedFlagsService {

    private final ArcRepository arcRepository;
    private final ChapterRepository chapterRepository;

    public CampaignReferencedFlagsService(ArcRepository arcRepository,
                                          ChapterRepository chapterRepository) {
        this.arcRepository = arcRepository;
        this.chapterRepository = chapterRepository;
    }

    /** Retourne la liste triée alphabétiquement des noms de faits référencés. */
    public List<String> listForCampaign(String campaignId) {
        TreeSet<String> unique = new TreeSet<>();
        for (Arc arc : arcRepository.findByCampaignId(campaignId)) {
            for (Chapter chapter : chapterRepository.findByArcId(arc.getId())) {
                if (chapter.getPrerequisites() == null) continue;
                for (Prerequisite p : chapter.getPrerequisites()) {
                    if (p instanceof Prerequisite.FlagSet f && f.flagName() != null && !f.flagName().isBlank()) {
                        unique.add(f.flagName());
                    }
                }
            }
        }
        return List.copyOf(unique);
    }
}
