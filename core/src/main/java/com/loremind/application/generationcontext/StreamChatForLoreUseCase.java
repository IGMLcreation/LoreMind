package com.loremind.application.generationcontext;

import com.loremind.domain.generationcontext.ChatMessage;
import com.loremind.domain.generationcontext.ChatRequest;
import com.loremind.domain.generationcontext.LoreStructuralContext;
import com.loremind.domain.generationcontext.PageContext;
import com.loremind.domain.generationcontext.ports.AiChatProvider;
import com.loremind.domain.lorecontext.Page;
import com.loremind.domain.lorecontext.Template;
import com.loremind.domain.lorecontext.ports.PageRepository;
import com.loremind.domain.lorecontext.ports.TemplateRepository;
import org.springframework.stereotype.Service;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;

/**
 * Use case applicatif : chat conversationnel avec Structural Context d'un Lore.
 * <p>
 * Orchestrateur fin — délègue la construction du LoreStructuralContext au
 * {@link LoreStructuralContextBuilder} (service partagé avec
 * {@link StreamChatForCampaignUseCase}), charge le PageContext si demandé,
 * puis délègue au port AiChatProvider pour le streaming.
 * <p>
 * Zéro persistance : la conversation est éphémère (responsabilité du frontend).
 */
@Service
public class StreamChatForLoreUseCase {

    private final LoreStructuralContextBuilder loreContextBuilder;
    private final PageRepository pageRepository;
    private final TemplateRepository templateRepository;
    private final AiChatProvider aiChatProvider;

    public StreamChatForLoreUseCase(
            LoreStructuralContextBuilder loreContextBuilder,
            PageRepository pageRepository,
            TemplateRepository templateRepository,
            AiChatProvider aiChatProvider) {
        this.loreContextBuilder = loreContextBuilder;
        this.pageRepository = pageRepository;
        this.templateRepository = templateRepository;
        this.aiChatProvider = aiChatProvider;
    }

    /**
     * Streame la réponse du LLM pour le Lore donné avec la conversation fournie.
     * <p>
     * Méthode bloquante : retourne une fois le stream terminé (onComplete ou onError).
     * L'appelant (controller SSE) doit l'exécuter dans un thread dédié.
     *
     * @param loreId obligatoire — l'univers concerné
     * @param pageId optionnel (nullable) — si fourni, focalise l'IA sur cette page
     *               précise (template, champs, valeurs actuelles).
     * @throws IllegalArgumentException si le Lore (ou la Page si pageId fourni) est introuvable
     */
    public void execute(
            String loreId,
            String pageId,
            List<ChatMessage> messages,
            Consumer<String> onToken,
            Runnable onComplete,
            Consumer<Throwable> onError) {

        LoreStructuralContext loreContext = loreContextBuilder.build(loreId);
        PageContext pageContext = (pageId == null || pageId.isBlank())
                ? null
                : buildPageContext(pageId);

        ChatRequest request = ChatRequest.builder()
                .messages(messages)
                .loreContext(loreContext)
                .pageContext(pageContext)
                .build();

        aiChatProvider.streamChat(request, onToken, onComplete, onError);
    }

    /**
     * Charge la Page + son Template et construit un PageContext prêt à injecter.
     * Si le template est absent (page orpheline), on renvoie un PageContext
     * minimal (titre + template "?", champs vides) — l'IA reste contextualisée
     * sur la page sans pouvoir proposer de champs précis.
     */
    private PageContext buildPageContext(String pageId) {
        Page page = pageRepository.findById(pageId)
                .orElseThrow(() -> new IllegalArgumentException(
                        "Page non trouvée avec l'ID: " + pageId));

        String templateName = "?";
        List<String> templateFields = Collections.emptyList();
        if (page.hasTemplate()) {
            Template template = templateRepository.findById(page.getTemplateId()).orElse(null);
            if (template != null) {
                templateName = template.getName();
                // On expose uniquement les noms des champs TEXT a l'IA pour le chat.
                // Les champs IMAGE ne sont pas pertinents pour une generation textuelle.
                templateFields = template.textFieldNames();
            }
        }

        Map<String, String> values = page.getValues() != null
                ? page.getValues()
                : Collections.emptyMap();

        return PageContext.builder()
                .title(page.getTitle())
                .templateName(templateName)
                .templateFields(templateFields)
                .values(values)
                .build();
    }
}
