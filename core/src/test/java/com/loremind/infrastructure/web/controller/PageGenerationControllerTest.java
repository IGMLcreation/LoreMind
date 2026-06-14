package com.loremind.infrastructure.web.controller;

import com.loremind.application.generationcontext.GeneratePageValuesUseCase;
import com.loremind.domain.generationcontext.ports.AiProviderException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.web.servlet.MockMvc;

import java.util.Map;

import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration pour {@link PageGenerationController} (generation IA de Page).
 * <p>
 * Le use case {@link GeneratePageValuesUseCase} est mocke : il orchestre l'appel
 * au Brain (AiProvider), indisponible en test. On verifie le mapping des
 * exceptions du use case vers les codes HTTP :
 * <ul>
 *   <li>OK -> 200 + {@code {values:{...}}}</li>
 *   <li>IllegalArgumentException (page introuvable) -> 404</li>
 *   <li>AiProviderException (Brain HS) -> 502</li>
 *   <li>IllegalStateException "aucun champ" -> 422</li>
 *   <li>IllegalStateException autre (incoherence BDD) -> 500</li>
 * </ul>
 */
@SpringBootTest
@AutoConfigureMockMvc
class PageGenerationControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockBean private GeneratePageValuesUseCase generatePageValuesUseCase;

    @Test
    void generate_returns200_withSuggestions() throws Exception {
        when(generatePageValuesUseCase.execute(eq("p1")))
                .thenReturn(Map.of("nom", "Aldric", "race", "Elfe"));

        mockMvc.perform(post("/api/pages/{id}/generate", "p1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.values.nom").value("Aldric"))
                .andExpect(jsonPath("$.values.race").value("Elfe"));
    }

    @Test
    void generate_returns404_whenPageMissing() throws Exception {
        when(generatePageValuesUseCase.execute(eq("missing")))
                .thenThrow(new IllegalArgumentException("Page non trouvée"));

        mockMvc.perform(post("/api/pages/{id}/generate", "missing"))
                .andExpect(status().isNotFound());
    }

    @Test
    void generate_returns502_whenBrainDown() throws Exception {
        when(generatePageValuesUseCase.execute(eq("p1")))
                .thenThrow(new AiProviderException("Brain unreachable"));

        mockMvc.perform(post("/api/pages/{id}/generate", "p1"))
                .andExpect(status().isBadGateway());
    }

    @Test
    void generate_returns422_whenTemplateHasNoFields() throws Exception {
        when(generatePageValuesUseCase.execute(eq("p1")))
                .thenThrow(new IllegalStateException("Le template 'X' n'a aucun champ texte à générer."));

        mockMvc.perform(post("/api/pages/{id}/generate", "p1"))
                .andExpect(status().isUnprocessableEntity());
    }

    @Test
    void generate_returns500_whenBddInconsistent() throws Exception {
        when(generatePageValuesUseCase.execute(eq("p1")))
                .thenThrow(new IllegalStateException("Template introuvable (id=t1)"));

        mockMvc.perform(post("/api/pages/{id}/generate", "p1"))
                .andExpect(status().isInternalServerError());
    }
}
