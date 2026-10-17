package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.ports.GuidedMethodAssistant;
import com.loremind.domain.campaigncontext.ports.GuidedMethodAssistant.Choice;
import com.loremind.domain.campaigncontext.ports.GuidedMethodAssistant.Question;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/** IA des méthodes guidées : questions validées, contexte de campagne transmis, réponses filtrées. */
@ExtendWith(MockitoExtension.class)
class GuidedAssistServiceTest {

    @Mock private GuidedMethodAssistant assistant;
    @Mock private CampaignContextFormatter contextFormatter;
    @InjectMocks private GuidedAssistService service;

    private static final Question TITLE = new Question("title", "Nom de l'enquête", "Ex. : L'héritage", "Le mystère", List.of());
    private static final Question NATURE = new Question("node1.nature", "Genre de scène", null, "Première piste",
            List.of(new Choice("location", "Un lieu"), new Choice("npc", "Une personne")));

    @Test
    void suggest_sendsCampaignContextAndExistingAnswers_andFiltersTheResult() {
        when(contextFormatter.format("c1")).thenReturn("Campagne : Valfroide");
        when(assistant.answer(eq("Enquête en nœuds"), any(), eq("Une tour se rallume"), eq("Campagne : Valfroide"),
                anyList(), anyMap())).thenReturn(Map.of(
                        "title", "  L'héritage empoisonné ",
                        "node1.nature", "dragon",       // choix invalide -> écarté
                        "invented", "clé inventée",     // clé non demandée -> écartée
                        "node1.name", ""));

        Map<String, String> out = service.suggest("c1", "Enquête en nœuds", "Trois pistes",
                "Une tour se rallume", List.of(TITLE, NATURE), Map.of("truth", " Le notaire ", "blank", " "));

        assertEquals(Map.of("title", "L'héritage empoisonné"), out);
        verify(assistant).answer(any(), any(), any(), any(), eq(List.of(TITLE, NATURE)), eq(Map.of("truth", "Le notaire")));
    }

    @Test
    void suggest_keepsAValidChoice() {
        when(assistant.answer(any(), any(), any(), any(), anyList(), anyMap())).thenReturn(Map.of("node1.nature", "npc"));
        assertEquals(Map.of("node1.nature", "npc"), service.suggest("c1", "M", "", "", List.of(NATURE), null));
    }

    @Test
    void suggest_withoutAnyValidQuestion_isRejected() {
        assertThrows(IllegalArgumentException.class, () -> service.suggest("c1", "M", "", "idée", List.of(), Map.of()));
        assertThrows(IllegalArgumentException.class, () -> service.suggest("c1", "M", "", "idée",
                java.util.Arrays.asList(null, new Question(" ", "x", null, null, null)), Map.of()));
    }
}
