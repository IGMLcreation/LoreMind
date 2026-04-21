package com.loremind.application.generationcontext;

import com.loremind.domain.generationcontext.GenerationContext;
import com.loremind.domain.generationcontext.GenerationResult;
import com.loremind.domain.generationcontext.ports.AiProvider;
import com.loremind.domain.lorecontext.Lore;
import com.loremind.domain.lorecontext.LoreNode;
import com.loremind.domain.lorecontext.Page;
import com.loremind.domain.lorecontext.Template;
import com.loremind.domain.lorecontext.ports.LoreNodeRepository;
import com.loremind.domain.lorecontext.ports.LoreRepository;
import com.loremind.domain.lorecontext.ports.PageRepository;
import com.loremind.domain.lorecontext.ports.TemplateRepository;
import org.springframework.stereotype.Service;

import java.util.Map;

/**
 * Use case applicatif : génère des suggestions de valeurs pour les champs
 * d'une Page via l'IA.
 * <p>
 * Orchestrateur (couche Application de l'hexagonal). C'est le seul endroit
 * qui touche simultanément au LoreContext (chargement) et au GenerationContext
 * (appel IA). Le domaine reste isolé.
 * <p>
 * Décision produit : ce use case NE PERSISTE PAS les valeurs générées.
 * Il renvoie des suggestions que l'utilisateur validera manuellement via
 * le endpoint PUT /api/pages/{id} existant.
 */
@Service
public class GeneratePageValuesUseCase {

    private final PageRepository pageRepository;
    private final TemplateRepository templateRepository;
    private final LoreRepository loreRepository;
    private final LoreNodeRepository loreNodeRepository;
    private final AiProvider aiProvider;

    public GeneratePageValuesUseCase(
            PageRepository pageRepository,
            TemplateRepository templateRepository,
            LoreRepository loreRepository,
            LoreNodeRepository loreNodeRepository,
            AiProvider aiProvider) {
        this.pageRepository = pageRepository;
        this.templateRepository = templateRepository;
        this.loreRepository = loreRepository;
        this.loreNodeRepository = loreNodeRepository;
        this.aiProvider = aiProvider;
    }

    /**
     * Génère les valeurs suggérées pour les champs dynamiques d'une Page.
     *
     * @param pageId identifiant de la Page à enrichir
     * @return map fieldName -> valeur suggérée (jamais null, peut contenir des chaînes vides)
     * @throws IllegalArgumentException si la Page est introuvable
     * @throws IllegalStateException    si le Template, le Lore ou le dossier parent sont
     *                                   incohérents (intégrité BDD cassée) ou si le Template
     *                                   n'a aucun champ à générer
     */
    public Map<String, String> execute(String pageId) {
        Page page = loadPage(pageId);
        Template template = loadTemplate(page.getTemplateId(), pageId);
        Lore lore = loadLore(page.getLoreId(), pageId);
        LoreNode folder = loadFolder(page.getNodeId(), pageId);

        requireNonEmptyFields(template);

        GenerationContext context = GenerationContext.builder()
                .loreName(lore.getName())
                .loreDescription(lore.getDescription())
                .folderName(folder.getName())
                .templateName(template.getName())
                // Seuls les champs TEXT sont envoyes a l'IA : les champs IMAGE
                // necessitent un workflow different (pas de generation LLM texte).
                .templateFields(template.textFieldNames())
                .pageTitle(page.getTitle())
                .build();

        GenerationResult result = aiProvider.generatePage(context);
        return result.values();
    }

    // --- Helpers de chargement (un lookup = un message d'erreur clair) ------

    private Page loadPage(String pageId) {
        return pageRepository.findById(pageId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Page non trouvée avec l'ID: " + pageId));
    }

    private Template loadTemplate(String templateId, String pageId) {
        if (templateId == null || templateId.isBlank()) {
            throw new IllegalStateException(
                    "La page " + pageId + " n'a pas de template associé.");
        }
        return templateRepository.findById(templateId)
                .orElseThrow(() -> new IllegalStateException(
                        "Template introuvable (id=" + templateId
                                + ") pour la page " + pageId));
    }

    private Lore loadLore(String loreId, String pageId) {
        return loreRepository.findById(loreId)
                .orElseThrow(() -> new IllegalStateException(
                        "Lore introuvable (id=" + loreId
                                + ") pour la page " + pageId));
    }

    private LoreNode loadFolder(String nodeId, String pageId) {
        return loreNodeRepository.findById(nodeId)
                .orElseThrow(() -> new IllegalStateException(
                        "Dossier parent introuvable (id=" + nodeId
                                + ") pour la page " + pageId));
    }

    private void requireNonEmptyFields(Template template) {
        // On exige au moins un champ TEXT : les champs IMAGE ne sont pas genereables
        // par l'IA (pas de text-to-image pour l'instant).
        if (template.textFieldNames().isEmpty()) {
            throw new IllegalStateException(
                    "Le template '" + template.getName()
                            + "' n'a aucun champ texte à générer.");
        }
    }
}
