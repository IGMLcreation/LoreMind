package com.loremind.infrastructure.ai;

import com.loremind.domain.generationcontext.GenerationContext;
import com.loremind.domain.generationcontext.GenerationResult;
import com.loremind.domain.generationcontext.ports.AiProviderException;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires PURS (JUnit 5 + Mockito, sans Spring, sans reseau) pour
 * BrainAiClient. Le RestTemplate est mocke ; on couvre toutes les branches
 * de callBrain ainsi que la traduction domaine -> wire -> domaine.
 */
class BrainAiClientTest {

    private static final String BASE_URL = "http://brain";
    private static final String EXPECTED_URL = "http://brain/generate-page";

    private GenerationContext sampleContext() {
        return new GenerationContext(
                "Aetheria",
                "Un monde de cendres",
                "PNJ",
                "Fiche personnage",
                List.of("histoire", "motto"),
                "Garde rouge"
        );
    }

    private BrainGeneratePageResponse responseWith(Map<String, String> values) {
        BrainGeneratePageResponse r = new BrainGeneratePageResponse();
        r.setValues(values);
        return r;
    }

    // --- Branche succes ------------------------------------------------------

    @Test
    void generatePage_succes_traduitReponseWireEnResultatDomaine() {
        RestTemplate rt = mock(RestTemplate.class);
        BrainAiClient client = new BrainAiClient(rt, BASE_URL);

        BrainGeneratePageResponse wire = responseWith(Map.of(
                "histoire", "Nee sous une etoile rouge",
                "motto", "Jamais genou en terre"
        ));
        when(rt.postForObject(anyString(), any(), eq(BrainGeneratePageResponse.class)))
                .thenReturn(wire);

        GenerationResult result = client.generatePage(sampleContext());

        assertEquals(2, result.values().size());
        assertEquals("Jamais genou en terre", result.values().get("motto"));
    }

    @Test
    void generatePage_appelleBonneUrlEtContentTypeJson_avecCorpsTraduit() {
        RestTemplate rt = mock(RestTemplate.class);
        BrainAiClient client = new BrainAiClient(rt, BASE_URL);

        when(rt.postForObject(anyString(), any(), eq(BrainGeneratePageResponse.class)))
                .thenReturn(responseWith(Map.of("histoire", "v")));

        client.generatePage(sampleContext());

        // Capture de l'URL et de l'HttpEntity envoyes au RestTemplate
        ArgumentCaptor<String> urlCaptor = ArgumentCaptor.forClass(String.class);
        @SuppressWarnings("unchecked")
        ArgumentCaptor<HttpEntity<BrainGeneratePageRequest>> entityCaptor =
                ArgumentCaptor.forClass(HttpEntity.class);

        org.mockito.Mockito.verify(rt).postForObject(
                urlCaptor.capture(),
                entityCaptor.capture(),
                eq(BrainGeneratePageResponse.class));

        assertEquals(EXPECTED_URL, urlCaptor.getValue());

        HttpEntity<BrainGeneratePageRequest> entity = entityCaptor.getValue();
        HttpHeaders headers = entity.getHeaders();
        assertEquals(MediaType.APPLICATION_JSON, headers.getContentType());

        // Verifie la traduction domaine -> wire (exerce les getters du record)
        BrainGeneratePageRequest body = entity.getBody();
        assertEquals("Aetheria", body.loreName());
        assertEquals("Un monde de cendres", body.loreDescription());
        assertEquals("PNJ", body.folderName());
        assertEquals("Fiche personnage", body.templateName());
        assertEquals(List.of("histoire", "motto"), body.templateFields());
        assertEquals("Garde rouge", body.pageTitle());
    }

    // --- Branche reponse null ------------------------------------------------

    @Test
    void generatePage_reponseNull_leveAiProviderException() {
        RestTemplate rt = mock(RestTemplate.class);
        BrainAiClient client = new BrainAiClient(rt, BASE_URL);

        when(rt.postForObject(anyString(), any(), eq(BrainGeneratePageResponse.class)))
                .thenReturn(null);

        AiProviderException ex = assertThrows(AiProviderException.class,
                () -> client.generatePage(sampleContext()));
        assertTrue(ex.getMessage().contains("reponse vide")
                || ex.getMessage().contains("réponse vide"));
    }

    // --- Branche values null -------------------------------------------------

    @Test
    void generatePage_valuesNull_leveAiProviderException() {
        RestTemplate rt = mock(RestTemplate.class);
        BrainAiClient client = new BrainAiClient(rt, BASE_URL);

        // Reponse non null mais avec values == null
        when(rt.postForObject(anyString(), any(), eq(BrainGeneratePageResponse.class)))
                .thenReturn(responseWith(null));

        assertThrows(AiProviderException.class,
                () -> client.generatePage(sampleContext()));
    }

    // --- Branche ResourceAccessException (Brain injoignable) -----------------

    @Test
    void generatePage_brainInjoignable_leveAiProviderException() {
        RestTemplate rt = mock(RestTemplate.class);
        BrainAiClient client = new BrainAiClient(rt, BASE_URL);

        ResourceAccessException cause = new ResourceAccessException("down");
        when(rt.postForObject(anyString(), any(), eq(BrainGeneratePageResponse.class)))
                .thenThrow(cause);

        AiProviderException ex = assertThrows(AiProviderException.class,
                () -> client.generatePage(sampleContext()));
        assertTrue(ex.getMessage().contains("injoignable"));
        assertSame(cause, ex.getCause());
    }

    // --- Branche RestClientResponseException (HTTP 4xx/5xx) ------------------

    @Test
    void generatePage_erreurHttp_leveAiProviderExceptionAvecCode() {
        RestTemplate rt = mock(RestTemplate.class);
        BrainAiClient client = new BrainAiClient(rt, BASE_URL);

        HttpServerErrorException cause = HttpServerErrorException.create(
                HttpStatus.BAD_GATEWAY, "Bad Gateway",
                new HttpHeaders(), new byte[0], null);
        when(rt.postForObject(anyString(), any(), eq(BrainGeneratePageResponse.class)))
                .thenThrow(cause);

        AiProviderException ex = assertThrows(AiProviderException.class,
                () -> client.generatePage(sampleContext()));
        assertTrue(ex.getMessage().contains("502"));
        assertSame(cause, ex.getCause());
    }

    // --- Branche AiProviderException deja traduite : re-levee telle quelle ---

    @Test
    void generatePage_aiProviderExceptionDejaTraduite_estRelancee() {
        RestTemplate rt = mock(RestTemplate.class);
        BrainAiClient client = new BrainAiClient(rt, BASE_URL);

        AiProviderException original = new AiProviderException("deja traduite");
        when(rt.postForObject(anyString(), any(), eq(BrainGeneratePageResponse.class)))
                .thenThrow(original);

        AiProviderException ex = assertThrows(AiProviderException.class,
                () -> client.generatePage(sampleContext()));
        // Pas de re-enveloppement : c'est exactement la meme instance
        assertSame(original, ex);
    }

    // --- Branche Exception generique (filet de securite) ---------------------

    @Test
    void generatePage_exceptionGenerique_leveAiProviderException() {
        RestTemplate rt = mock(RestTemplate.class);
        BrainAiClient client = new BrainAiClient(rt, BASE_URL);

        RuntimeException cause = new IllegalStateException("JSON invalide");
        when(rt.postForObject(anyString(), any(), eq(BrainGeneratePageResponse.class)))
                .thenThrow(cause);

        AiProviderException ex = assertThrows(AiProviderException.class,
                () -> client.generatePage(sampleContext()));
        assertTrue(ex.getMessage().contains("inattendue"));
        assertSame(cause, ex.getCause());
    }
}
