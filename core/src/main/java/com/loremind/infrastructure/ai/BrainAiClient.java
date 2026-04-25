package com.loremind.infrastructure.ai;

import com.loremind.domain.generationcontext.GenerationContext;
import com.loremind.domain.generationcontext.GenerationResult;
import com.loremind.domain.generationcontext.ports.AiProvider;
import com.loremind.domain.generationcontext.ports.AiProviderException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

import java.util.Map;

/**
 * Adapter de sortie : implémente le port AiProvider en appelant
 * le Brain Python via HTTP (RestTemplate).
 * <p>
 * Responsabilités exclusives de cette classe :
 *  1. Traduire GenerationContext (domaine) -> BrainGeneratePageRequest (wire).
 *  2. Exécuter l'appel HTTP POST /generate-page.
 *  3. Traduire BrainGeneratePageResponse (wire) -> GenerationResult (domaine).
 *  4. Traduire toute erreur technique en AiProviderException (exception de domaine).
 * <p>
 * Le domaine ne voit JAMAIS RestTemplate, Jackson, ni la moindre URL.
 */
@Component
public class BrainAiClient implements AiProvider {

    private static final String GENERATE_PAGE_PATH = "/generate-page";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public BrainAiClient(
            RestTemplate restTemplate,
            @Value("${brain.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    public GenerationResult generatePage(GenerationContext context) {
        BrainGeneratePageRequest request = toBrainRequest(context);
        BrainGeneratePageResponse response = callBrain(request);
        return toDomainResult(response);
    }

    // --- Traduction domaine -> wire -----------------------------------------

    private BrainGeneratePageRequest toBrainRequest(GenerationContext context) {
        return new BrainGeneratePageRequest(
                context.loreName(),
                context.loreDescription(),
                context.folderName(),
                context.templateName(),
                context.templateFields(),
                context.pageTitle()
        );
    }

    // --- Appel HTTP + traduction d'erreurs ----------------------------------

    private BrainGeneratePageResponse callBrain(BrainGeneratePageRequest request) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.APPLICATION_JSON);
        HttpEntity<BrainGeneratePageRequest> entity = new HttpEntity<>(request, headers);

        try {
            BrainGeneratePageResponse response = restTemplate.postForObject(
                    baseUrl + GENERATE_PAGE_PATH,
                    entity,
                    BrainGeneratePageResponse.class
            );
            if (response == null || response.getValues() == null) {
                throw new AiProviderException("Le Brain a renvoyé une réponse vide.");
            }
            return response;
        } catch (ResourceAccessException e) {
            // Timeout ou connexion impossible (Brain down)
            throw new AiProviderException(
                    "Le Brain est injoignable (timeout ou service arrêté).", e);
        } catch (RestClientResponseException e) {
            // Code HTTP 4xx/5xx renvoyé par le Brain
            throw new AiProviderException(
                    "Le Brain a répondu avec une erreur HTTP " + e.getStatusCode().value(), e);
        } catch (AiProviderException e) {
            throw e; // déjà traduite, ne pas ré-envelopper
        } catch (Exception e) {
            // Filet de sécurité (JSON invalide, etc.)
            throw new AiProviderException(
                    "Erreur inattendue lors de l'appel au Brain.", e);
        }
    }

    // --- Traduction wire -> domaine -----------------------------------------

    private GenerationResult toDomainResult(BrainGeneratePageResponse response) {
        return new GenerationResult(Map.copyOf(response.getValues()));
    }
}
