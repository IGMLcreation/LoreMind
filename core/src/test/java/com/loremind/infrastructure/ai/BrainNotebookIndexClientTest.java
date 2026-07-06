package com.loremind.infrastructure.ai;

import com.loremind.domain.campaigncontext.ports.exceptions.NotebookException;
import com.loremind.domain.campaigncontext.ports.NotebookIndexer.IndexResult;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpServerErrorException;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestTemplate;

import java.lang.reflect.Constructor;
import java.lang.reflect.Field;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doReturn;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires purs (JUnit 5 + Mockito, sans Spring/réseau) de
 * {@link BrainNotebookIndexClient} : indexation multipart one-shot (RestTemplate)
 * et suppression best-effort.
 * <p>
 * {@code IndexResponse} étant une classe privée de l'adapter, on l'instancie par
 * réflexion pour piloter la valeur renvoyée par le mock RestTemplate.
 */
class BrainNotebookIndexClientTest {

    private static final String BASE_URL = "http://brain";

    private RestTemplate restTemplate;
    private BrainNotebookIndexClient client;

    @BeforeEach
    void setUp() {
        restTemplate = mock(RestTemplate.class);
        client = new BrainNotebookIndexClient(restTemplate, BASE_URL);
    }

    /** Récupère la Class<?> de l'IndexResponse privée (telle qu'attendue par postForObject). */
    private Class<?> indexResponseClass() throws ClassNotFoundException {
        return Class.forName("com.loremind.infrastructure.ai.BrainNotebookIndexClient$IndexResponse");
    }

    /** Instancie l'IndexResponse privée et remplit ses champs par réflexion. */
    private Object newIndexResponse(int chunks, int pageCount, int ocrPageCount) throws Exception {
        Class<?> cls = indexResponseClass();
        Constructor<?> ctor = cls.getDeclaredConstructor();
        ctor.setAccessible(true);
        Object resp = ctor.newInstance();
        setField(resp, "chunks", chunks);
        setField(resp, "pageCount", pageCount);
        setField(resp, "ocrPageCount", ocrPageCount);
        return resp;
    }

    private void setField(Object target, String name, int value) throws Exception {
        Field f = target.getClass().getDeclaredField(name);
        f.setAccessible(true);
        f.setInt(target, value);
    }

    // --- index() -------------------------------------------------------------

    @Test
    @SuppressWarnings("unchecked")
    void index_succes_retourneIndexResult() throws Exception {
        Object wire = newIndexResponse(42, 100, 7);
        // doReturn évite l'inférence générique (Class<?> -> wildcard) qui casse thenReturn.
        doReturn(wire).when(restTemplate)
                .postForObject(anyString(), any(), eq(indexResponseClass()));

        IndexResult result = client.index("src-1", new byte[]{1, 2, 3}, "livre.pdf");

        assertEquals(42, result.chunks());
        assertEquals(100, result.pageCount());
        assertEquals(7, result.ocrPageCount());

        // L'URL appelée concatène baseUrl + INDEX_PATH.
        ArgumentCaptor<String> url = ArgumentCaptor.forClass(String.class);
        verify(restTemplate).postForObject(url.capture(), any(), eq(indexResponseClass()));
        assertEquals(BASE_URL + "/index/notebook-source", url.getValue());
    }

    @Test
    void index_filenameNull_utiliseNomParDefaut() throws Exception {
        Object wire = newIndexResponse(1, 1, 0);
        doReturn(wire).when(restTemplate)
                .postForObject(anyString(), any(), eq(indexResponseClass()));

        // filename null/blank -> branche "source.pdf" du filePart.
        IndexResult result = client.index("src-1", new byte[]{9}, null);
        assertEquals(1, result.chunks());
    }

    @Test
    void index_reponseNull_leveNotebookException() {
        when(restTemplate.postForObject(anyString(), any(), any(Class.class)))
                .thenReturn(null);

        NotebookException ex = assertThrows(NotebookException.class,
                () -> client.index("src-1", new byte[]{1}, "f.pdf"));
        assertTrue(ex.getMessage().contains("vide"));
    }

    @Test
    void index_resourceAccess_leveNotebookException() {
        when(restTemplate.postForObject(anyString(), any(), any(Class.class)))
                .thenThrow(new ResourceAccessException("timeout"));

        NotebookException ex = assertThrows(NotebookException.class,
                () -> client.index("src-1", new byte[]{1}, "f.pdf"));
        assertTrue(ex.getMessage().contains("injoignable"));
    }

    @Test
    void index_httpServerError_leveNotebookExceptionAvecStatut() {
        HttpServerErrorException http = HttpServerErrorException.create(
                HttpStatus.BAD_GATEWAY, "Bad Gateway", new HttpHeaders(), new byte[0], null);
        when(restTemplate.postForObject(anyString(), any(), any(Class.class)))
                .thenThrow(http);

        NotebookException ex = assertThrows(NotebookException.class,
                () -> client.index("src-1", new byte[]{1}, "f.pdf"));
        assertTrue(ex.getMessage().contains("502"));
    }

    @Test
    void index_erreurInattendue_leveNotebookException() {
        when(restTemplate.postForObject(anyString(), any(), any(Class.class)))
                .thenThrow(new IllegalStateException("boom"));

        NotebookException ex = assertThrows(NotebookException.class,
                () -> client.index("src-1", new byte[]{1}, "f.pdf"));
        assertTrue(ex.getMessage().contains("inattendue"));
    }

    // --- delete() ------------------------------------------------------------

    @Test
    void delete_appelleRestTemplateAvecBonneUrl() {
        client.delete("src-99");
        verify(restTemplate).delete(BASE_URL + "/index/notebook-source/src-99");
    }

    @Test
    void delete_erreurIgnoree_neRelancePas() {
        // Best-effort : une exception du RestTemplate est avalée (log warn).
        doThrow(new ResourceAccessException("down"))
                .when(restTemplate).delete(anyString());

        // Ne doit pas lever.
        client.delete("src-99");
        verify(restTemplate).delete(BASE_URL + "/index/notebook-source/src-99");
    }
}
