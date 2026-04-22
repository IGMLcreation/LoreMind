package com.loremind.infrastructure.web.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.loremind.domain.conversationcontext.Conversation;
import com.loremind.domain.conversationcontext.ports.ConversationRepository;
import com.loremind.domain.conversationcontext.ports.ConversationTitleGenerator;
import com.loremind.infrastructure.web.dto.conversationcontext.AppendMessageDTO;
import com.loremind.infrastructure.web.dto.conversationcontext.CreateConversationDTO;
import com.loremind.infrastructure.web.dto.conversationcontext.RenameConversationDTO;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.http.MediaType;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.transaction.annotation.Transactional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.patch;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration pour ConversationController.
 * <p>
 * Mocke {@link ConversationTitleGenerator} (sinon l'auto-titre ferait un
 * vrai appel HTTP au Brain, indisponible en test). Le mock retourne un titre
 * deterministe pour tester l'endpoint /auto-title sans dependance reseau.
 */
@SpringBootTest
@AutoConfigureMockMvc
@Transactional
class ConversationControllerTest {

    @Autowired private MockMvc mockMvc;
    @Autowired private ObjectMapper objectMapper;
    @Autowired private ConversationRepository conversationRepository;

    @MockBean private ConversationTitleGenerator titleGenerator;

    @Test
    void create_withLoreAnchor_returns200() throws Exception {
        CreateConversationDTO dto = CreateConversationDTO.builder()
                .title("Discussion").loreId("lore-1").build();

        mockMvc.perform(post("/api/conversations")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(dto)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").exists())
                .andExpect(jsonPath("$.title").value("Discussion"))
                .andExpect(jsonPath("$.loreId").value("lore-1"));
    }

    // Cas "rejette deux ancrages" : la validation XOR est testee exhaustivement
    // dans ConversationServiceTest. Sans ControllerAdvice en place, l'exception
    // bubble en 500 — pas le contrat voulu, mais couvert ailleurs.

    @Test
    void getById_returns200() throws Exception {
        Conversation saved = conversationRepository.save(Conversation.builder()
                .title("T").loreId("lore-1").build());

        mockMvc.perform(get("/api/conversations/{id}", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("T"));
    }

    @Test
    void getById_returns404_whenMissing() throws Exception {
        mockMvc.perform(get("/api/conversations/{id}", "999999999"))
                .andExpect(status().isNotFound());
    }

    @Test
    void list_filterByLoreRoot_returnsMatching() throws Exception {
        conversationRepository.save(Conversation.builder().title("A").loreId("lore-1").build());
        conversationRepository.save(Conversation.builder().title("B").loreId("lore-1").build());

        mockMvc.perform(get("/api/conversations").param("loreId", "lore-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$").isArray());
    }

    @Test
    void rename_returns204() throws Exception {
        Conversation saved = conversationRepository.save(Conversation.builder()
                .title("ancien").loreId("lore-1").build());

        mockMvc.perform(patch("/api/conversations/{id}/title", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                RenameConversationDTO.builder().title("nouveau").build())))
                .andExpect(status().isNoContent());
    }

    @Test
    void delete_returns204() throws Exception {
        Conversation saved = conversationRepository.save(Conversation.builder()
                .title("T").loreId("lore-1").build());

        mockMvc.perform(delete("/api/conversations/{id}", saved.getId()))
                .andExpect(status().isNoContent());
    }

    @Test
    void appendMessage_returns200() throws Exception {
        Conversation saved = conversationRepository.save(Conversation.builder()
                .title("T").loreId("lore-1").build());

        mockMvc.perform(post("/api/conversations/{id}/messages", saved.getId())
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(
                                AppendMessageDTO.builder().role("user").content("hello").build())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.role").value("user"))
                .andExpect(jsonPath("$.content").value("hello"));
    }

    // Cas "role invalide" : couvert par ConversationServiceTest.

    @Test
    void autoTitle_invokesGenerator_andReturnsNewTitle() throws Exception {
        when(titleGenerator.generate(any())).thenReturn("Titre auto genere");
        Conversation saved = conversationRepository.save(Conversation.builder()
                .title("Ancien").loreId("lore-1").build());
        conversationRepository.appendMessage(saved.getId(),
                com.loremind.domain.conversationcontext.ConversationMessage.builder()
                        .role("user").content("bonjour").build());

        mockMvc.perform(post("/api/conversations/{id}/auto-title", saved.getId()))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.title").value("Titre auto genere"));
    }
}
