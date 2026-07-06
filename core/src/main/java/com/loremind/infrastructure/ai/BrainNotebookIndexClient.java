package com.loremind.infrastructure.ai;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.loremind.domain.campaigncontext.ports.exceptions.NotebookException;
import com.loremind.domain.campaigncontext.ports.NotebookIndexer;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Component;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.client.RestTemplate;

/**
 * Adapter de sortie : indexe une source de notebook via le Brain (multipart,
 * one-shot bloquant — l'indexation d'un livre peut prendre du temps, d'où le
 * RestTemplate à timeout long {@code brainImportRestTemplate}).
 */
@Component
public class BrainNotebookIndexClient implements NotebookIndexer {

    private static final Logger LOG = LoggerFactory.getLogger(BrainNotebookIndexClient.class);
    private static final String INDEX_PATH = "/index/notebook-source";

    private final RestTemplate restTemplate;
    private final String baseUrl;

    public BrainNotebookIndexClient(
            @Qualifier("brainImportRestTemplate") RestTemplate restTemplate,
            @Value("${brain.base-url}") String baseUrl) {
        this.restTemplate = restTemplate;
        this.baseUrl = baseUrl;
    }

    @Override
    public IndexResult index(String sourceId, byte[] pdfBytes, String filename) {
        HttpHeaders headers = new HttpHeaders();
        headers.setContentType(MediaType.MULTIPART_FORM_DATA);

        MultiValueMap<String, Object> body = new LinkedMultiValueMap<>();
        body.add("source_id", sourceId);
        body.add("file", filePart(pdfBytes, filename));
        HttpEntity<MultiValueMap<String, Object>> entity = new HttpEntity<>(body, headers);

        try {
            IndexResponse resp = restTemplate.postForObject(
                    baseUrl + INDEX_PATH, entity, IndexResponse.class);
            if (resp == null) {
                throw new NotebookException("Le Brain a renvoyé une réponse vide à l'indexation.");
            }
            return new IndexResult(resp.getChunks(), resp.getPageCount(), resp.getOcrPageCount());
        } catch (ResourceAccessException e) {
            throw new NotebookException("Le Brain est injoignable (timeout ou arrêté).", e);
        } catch (RestClientResponseException e) {
            throw new NotebookException(
                    "Le Brain a répondu HTTP " + e.getStatusCode().value()
                            + " : " + e.getResponseBodyAsString(), e);
        } catch (NotebookException e) {
            throw e;
        } catch (Exception e) {
            throw new NotebookException("Erreur inattendue lors de l'indexation via le Brain.", e);
        }
    }

    @Override
    public void delete(String sourceId) {
        // Best-effort : si le Brain est down, on ne bloque pas la suppression côté Core
        // (les vecteurs orphelins seront simplement ignorés / nettoyables plus tard).
        try {
            restTemplate.delete(baseUrl + INDEX_PATH + "/" + sourceId);
        } catch (Exception e) {
            LOG.warn("Suppression des vecteurs de la source {} échouée (ignorée) : {}", sourceId, e.getMessage());
        }
    }

    private ByteArrayResource filePart(byte[] pdfBytes, String filename) {
        return new ByteArrayResource(pdfBytes) {
            @Override
            public String getFilename() {
                return (filename == null || filename.isBlank()) ? "source.pdf" : filename;
            }
        };
    }

    /**
     * Réponse JSON du Brain (snake_case). Champs privés + @JsonProperty explicite
     * sur CHAQUE champ : Jackson n'auto-détecte que les champs publics par défaut,
     * l'annotation reste nécessaire pour que la désérialisation continue de fonctionner
     * une fois les champs rendus privés.
     */
    private static class IndexResponse {
        @JsonProperty("chunks")
        private int chunks;
        @JsonProperty("page_count")
        private int pageCount;
        @JsonProperty("ocr_page_count")
        private int ocrPageCount;

        int getChunks() { return chunks; }
        int getPageCount() { return pageCount; }
        int getOcrPageCount() { return ocrPageCount; }
    }
}
