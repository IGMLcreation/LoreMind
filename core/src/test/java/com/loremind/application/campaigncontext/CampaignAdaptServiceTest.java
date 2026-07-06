package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.ports.CampaignPdfAdvisor;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test unitaire pour CampaignAdaptService.
 * Mocks des ports (campagne, brief builder, advisor PDF du Brain).
 * Vérifie la délégation streamée et l'échec quand la campagne est introuvable.
 */
@ExtendWith(MockitoExtension.class)
class CampaignAdaptServiceTest {

    @Mock
    private CampaignRepository campaignRepository;
    @Mock
    private CampaignBriefBuilder briefBuilder;
    @Mock
    private CampaignPdfAdvisor advisor;

    @InjectMocks
    private CampaignAdaptService service;

    @Test
    void testAdviseStreaming_DelegatesWithBrief() {
        Campaign campaign = Campaign.builder().id("camp-1").name("Camp").build();
        when(campaignRepository.findById("camp-1")).thenReturn(Optional.of(campaign));
        when(briefBuilder.build(campaign)).thenReturn("BRIEF");

        byte[] pdf = {1, 2, 3};
        Consumer<String> onToken = t -> {};
        Runnable onComplete = () -> {};
        Consumer<Throwable> onError = e -> {};

        service.adviseStreaming("camp-1", pdf, "doc.pdf", "[]", onToken, onComplete, onError);

        // Le brief construit doit être relayé tel quel à l'advisor, avec les mêmes callbacks.
        verify(advisor).adviseStreaming(pdf, "doc.pdf", "BRIEF", "[]", onToken, onComplete, onError);
    }

    @Test
    void testAdviseStreaming_CampaignNotFound_Throws() {
        when(campaignRepository.findById("missing")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.adviseStreaming("missing", new byte[]{1}, "doc.pdf", "[]",
                        t -> {}, () -> {}, e -> {}));
        assertEquals("Campagne introuvable : missing", ex.getMessage());

        verify(briefBuilder, never()).build(any());
        verifyNoInteractions(advisor);
    }
}
