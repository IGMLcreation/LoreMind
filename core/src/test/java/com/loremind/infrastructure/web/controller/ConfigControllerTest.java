package com.loremind.infrastructure.web.controller;

import com.loremind.infrastructure.updates.UpdateCheckService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration pour {@link ConfigController}.
 * <p>
 * GET /api/config renvoie {demoMode, updateCheckEnabled}. Le service de mise a
 * jour est mocke pour piloter la valeur de updateCheckEnabled sans dependre de
 * la configuration reelle (Watchtower/registry indisponibles en test).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ConfigControllerTest {

    @Autowired private MockMvc mockMvc;
    @MockitoBean private UpdateCheckService updates;

    @Test
    void getPublicConfig_returns200_updateCheckEnabledTrue() throws Exception {
        when(updates.isEnabled()).thenReturn(true);
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updateCheckEnabled").value(true))
                .andExpect(jsonPath("$.demoMode").value(false));
    }

    @Test
    void getPublicConfig_returns200_updateCheckEnabledFalse() throws Exception {
        when(updates.isEnabled()).thenReturn(false);
        mockMvc.perform(get("/api/config"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.updateCheckEnabled").value(false));
    }
}
