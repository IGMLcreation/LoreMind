package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.gamesystemcontext.GameSystem;
import com.loremind.domain.gamesystemcontext.ports.GameSystemRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.when;

/**
 * Test unitaire pour CampaignContextFormatter.
 * Mocks des ports (campagne, système de jeu).
 */
@ExtendWith(MockitoExtension.class)
class CampaignContextFormatterTest {

    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private GameSystemRepository gameSystemRepository;

    @InjectMocks
    private CampaignContextFormatter formatter;

    @Test
    void testFormat_NullCampaignId_ReturnsEmpty() {
        assertEquals("", formatter.format(null));
    }

    @Test
    void testFormat_CampaignNotFound_ReturnsEmpty() {
        when(campaignRepository.findById("camp-1")).thenReturn(Optional.empty());

        assertEquals("", formatter.format("camp-1"));
    }

    @Test
    void testFormat_NameOnly() {
        when(campaignRepository.findById("camp-1"))
                .thenReturn(Optional.of(Campaign.builder().id("camp-1").name("Camp").build()));

        assertEquals("Campagne : Camp", formatter.format("camp-1"));
    }

    @Test
    void testFormat_WithDescription_TrimsIt() {
        when(campaignRepository.findById("camp-1")).thenReturn(Optional.of(Campaign.builder()
                .id("camp-1").name("Camp").description(" Aventure ").build()));

        assertEquals("Campagne : Camp — Aventure", formatter.format("camp-1"));
    }

    @Test
    void testFormat_BlankDescription_Ignored() {
        when(campaignRepository.findById("camp-1")).thenReturn(Optional.of(Campaign.builder()
                .id("camp-1").name("Camp").description("   ").build()));

        assertEquals("Campagne : Camp", formatter.format("camp-1"));
    }

    @Test
    void testFormat_WithGameSystem_AppendsIt() {
        when(campaignRepository.findById("camp-1")).thenReturn(Optional.of(Campaign.builder()
                .id("camp-1").name("Camp").description("Aventure").gameSystemId("gs-1").build()));
        when(gameSystemRepository.findById("gs-1"))
                .thenReturn(Optional.of(GameSystem.builder().id("gs-1").name("Pathfinder").build()));

        assertEquals("Campagne : Camp — Aventure\nSystème de jeu : Pathfinder", formatter.format("camp-1"));
    }

    @Test
    void testFormat_GameSystemNotFound_Ignored() {
        when(campaignRepository.findById("camp-1")).thenReturn(Optional.of(Campaign.builder()
                .id("camp-1").name("Camp").gameSystemId("gs-1").build()));
        when(gameSystemRepository.findById("gs-1")).thenReturn(Optional.empty());

        assertEquals("Campagne : Camp", formatter.format("camp-1"));
    }
}
