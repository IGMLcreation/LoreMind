package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.GuidedAssistService;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.GuidedMethodAssistant.Question;
import com.loremind.domain.campaigncontext.ports.exceptions.NarrativeAssistException;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;

/**
 * IA des méthodes guidées : propose des réponses aux questions d'une méthode à partir de
 * l'idée de départ du MJ. Rien n'est enregistré.
 *
 * <p>{@code POST /api/campaigns/{campaignId}/guided-assist} → {@code {answers: {cle: valeur}}} ;
 * 400 sans question, 404 campagne inconnue, 502 si l'IA est indisponible.</p>
 */
@RestController
@RequestMapping("/api/campaigns/{campaignId}/guided-assist")
public class GuidedAssistController {

    /** Corps : la méthode (nom, principe), l'idée, les questions et les réponses déjà écrites. */
    public record SuggestRequest(String methodName, String principle, String idea,
                                 List<Question> questions, Map<String, String> existing) {}

    public record SuggestResponse(Map<String, String> answers) {}

    private final GuidedAssistService service;
    private final CampaignRepository campaignRepository;

    public GuidedAssistController(GuidedAssistService service, CampaignRepository campaignRepository) {
        this.service = service;
        this.campaignRepository = campaignRepository;
    }

    @PostMapping
    public ResponseEntity<SuggestResponse> suggest(@PathVariable String campaignId, @RequestBody SuggestRequest body) {
        if (!campaignRepository.existsById(campaignId)) {
            return ResponseEntity.notFound().build();
        }
        try {
            return ResponseEntity.ok(new SuggestResponse(service.suggest(campaignId, body.methodName(),
                    body.principle(), body.idea(), body.questions(), body.existing())));
        } catch (NarrativeAssistException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
    }
}
