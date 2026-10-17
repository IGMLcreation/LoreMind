package com.loremind.infrastructure.web.controller;

import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.GuidedMethodAssistant;
import com.loremind.domain.campaigncontext.ports.exceptions.NarrativeAssistException;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class GuidedAssistControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private CampaignRepository campaignRepository;
    @MockitoBean private GuidedMethodAssistant assistant;

    private static final String BODY = """
            {"methodName":"Enquête en nœuds","principle":"Trois pistes","idea":"Une tour se rallume",
             "questions":[{"key":"title","label":"Nom","hint":"","step":"Le mystère","options":[]}],
             "existing":{}}""";

    private String campaignId() {
        return campaignRepository.save(Campaign.builder().name("C").description("").build()).getId();
    }

    @Test
    void suggest_returnsTheFilteredAnswers() throws Exception {
        when(assistant.answer(any(), any(), any(), any(), anyList(), anyMap()))
                .thenReturn(Map.of("title", "L'héritage empoisonné", "other", "x"));
        mockMvc.perform(post("/api/campaigns/{id}/guided-assist", campaignId())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.answers.title").value("L'héritage empoisonné"))
                .andExpect(jsonPath("$.answers.other").doesNotExist());
    }

    @Test
    void suggest_unknownCampaign_is404_andUnavailableAi_is502() throws Exception {
        mockMvc.perform(post("/api/campaigns/{id}/guided-assist", "999999999")
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isNotFound());

        when(assistant.answer(any(), any(), any(), any(), anyList(), anyMap()))
                .thenThrow(new NarrativeAssistException("Le Brain est injoignable"));
        mockMvc.perform(post("/api/campaigns/{id}/guided-assist", campaignId())
                        .contentType(MediaType.APPLICATION_JSON).content(BODY))
                .andExpect(status().isBadGateway());
    }

    @Test
    void suggest_withoutQuestions_is400() throws Exception {
        mockMvc.perform(post("/api/campaigns/{id}/guided-assist", campaignId())
                        .contentType(MediaType.APPLICATION_JSON).content("{\"idea\":\"x\",\"questions\":[]}"))
                .andExpect(status().isBadRequest());
    }
}
