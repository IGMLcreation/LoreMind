package com.loremind.application.generationcontext;

import com.loremind.application.campaigncontext.CampaignContextFormatter;
import com.loremind.domain.campaigncontext.structure.Chapter;
import com.loremind.domain.campaigncontext.structure.Scene;
import com.loremind.domain.campaigncontext.generation.SceneDraft;
import com.loremind.domain.campaigncontext.generation.SceneDraftProposal;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.SceneDraftAssistant;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.stream.Collectors;

/**
 * Use case (Pilier A — capacité « create ») : produit une PROPOSITION d'ébauches de scènes
 * pour un chapitre (non persistée). Répond directement à la « page blanche » / au manque
 * de guidage « Chapitre vide » (Pilier B).
 *
 * <p>Contexte compact : le chapitre (objectifs/enjeux) + les scènes DÉJÀ présentes (pour
 * éviter les doublons) + méta campagne. Zéro écriture — la création est un second appel.</p>
 */
@Service
public class DraftScenesUseCase {

    private static final int MIN_COUNT = 1;
    private static final int MAX_COUNT = 8;

    private final ChapterRepository chapterRepository;
    private final SceneRepository sceneRepository;
    private final CampaignContextFormatter campaignContextFormatter;
    private final SceneDraftAssistant assistant;

    public DraftScenesUseCase(ChapterRepository chapterRepository,
                              SceneRepository sceneRepository,
                              CampaignContextFormatter campaignContextFormatter,
                              SceneDraftAssistant assistant) {
        this.chapterRepository = chapterRepository;
        this.sceneRepository = sceneRepository;
        this.campaignContextFormatter = campaignContextFormatter;
        this.assistant = assistant;
    }

    public SceneDraftProposal execute(String chapterId, String campaignId, String instruction, int count) {
        Chapter chapter = chapterRepository.findById(chapterId)
                .orElseThrow(() -> new IllegalArgumentException("Chapitre non trouvé: " + chapterId));
        int n = Math.max(MIN_COUNT, Math.min(MAX_COUNT, count));

        String context = buildContext(chapter, campaignId);
        List<SceneDraft> drafts = assistant.draftScenes(context, instruction, n).stream()
                .filter(d -> d != null && d.name() != null && !d.name().isBlank())
                .limit(n)
                .toList();
        return new SceneDraftProposal(chapterId, drafts);
    }

    private String buildContext(Chapter chapter, String campaignId) {
        StringBuilder sb = new StringBuilder();
        sb.append("Chapitre : ").append(blankToLabel(chapter.getName())).append("\n");
        appendIf(sb, "Synopsis", chapter.getDescription());
        appendIf(sb, "Objectifs des joueurs", chapter.getPlayerObjectives());
        appendIf(sb, "Enjeux narratifs", chapter.getNarrativeStakes());

        List<Scene> existing = sceneRepository.findByChapterId(chapter.getId());
        if (!existing.isEmpty()) {
            String names = existing.stream()
                    .map(Scene::getName)
                    .filter(nm -> nm != null && !nm.isBlank())
                    .collect(Collectors.joining(" ; "));
            if (!names.isEmpty()) {
                sb.append("Scènes DÉJÀ présentes (ne pas dupliquer) : ").append(names).append("\n");
            }
        }

        if (campaignId != null && !campaignId.isBlank()) {
            String campaignBlock = campaignContextFormatter.format(campaignId);
            if (!campaignBlock.isBlank()) {
                sb.append(campaignBlock).append("\n");
            }
        }
        return sb.toString();
    }

    private static void appendIf(StringBuilder sb, String label, String value) {
        if (value != null && !value.isBlank()) {
            sb.append(label).append(" : ").append(value.trim()).append("\n");
        }
    }

    private static String blankToLabel(String value) {
        return value == null || value.isBlank() ? "(sans titre)" : value;
    }
}
