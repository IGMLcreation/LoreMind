package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.RandomTableService;
import com.loremind.domain.campaigncontext.randomtable.RandomTable;
import com.loremind.domain.campaigncontext.ports.exceptions.RandomTableGenerationException;
import com.loremind.infrastructure.web.dto.campaigncontext.RandomTableDTO;
import com.loremind.infrastructure.web.mapper.RandomTableMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/random-tables")
public class RandomTableController {

    private final RandomTableService service;
    private final RandomTableMapper mapper;

    public RandomTableController(RandomTableService service, RandomTableMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<RandomTableDTO> create(@RequestBody RandomTableDTO dto) {
        RandomTable created = service.createTable(toData(dto, null));
        return ResponseEntity.ok(mapper.toDTO(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<RandomTableDTO> getById(@PathVariable String id) {
        return service.getTableById(id)
                .map(t -> ResponseEntity.ok(mapper.toDTO(t)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/campaign/{campaignId}")
    public ResponseEntity<List<RandomTableDTO>> getByCampaign(@PathVariable String campaignId) {
        List<RandomTableDTO> dtos = service.getTablesByCampaignId(campaignId).stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PutMapping("/{id}")
    public ResponseEntity<RandomTableDTO> update(@PathVariable String id, @RequestBody RandomTableDTO dto) {
        RandomTable updated = service.updateTable(id, toData(dto, dto.getOrder()));
        return ResponseEntity.ok(mapper.toDTO(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.deleteTable(id);
        return ResponseEntity.noContent().build();
    }

    /** Recherche par nom — alimente la recherche globale (Ctrl+K). */
    @GetMapping("/search")
    public ResponseEntity<List<RandomTableDTO>> search(@RequestParam("q") String query) {
        return ResponseEntity.ok(service.searchTables(query).stream()
                .map(mapper::toDTO)
                .collect(java.util.stream.Collectors.toList()));
    }

    /** Génère une PROPOSITION de table via l'IA (non persistée) — l'UI préremplit le formulaire. */
    @PostMapping("/generate")
    public ResponseEntity<RandomTableDTO> generate(@RequestBody GenerateRequest req) {
        try {
            RandomTable proposal = service.generateProposal(req.campaignId(), req.description(), req.diceFormula());
            return ResponseEntity.ok(mapper.toDTO(proposal));
        } catch (RandomTableGenerationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
    }

    /** Improvisation IA d'un court récit sur un résultat tiré (utilisé en partie). */
    @PostMapping("/improvise")
    public ResponseEntity<Map<String, String>> improvise(@RequestBody ImproviseRequest req) {
        try {
            String narration = service.improviseRoll(
                    req.campaignId(), req.tableName(), req.resultLabel(), req.resultDetail());
            return ResponseEntity.ok(Map.of("narration", narration));
        } catch (RandomTableGenerationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
    }

    /** Réordonne les tables aléatoires : order = position. */
    @PutMapping("/reorder")
    public ResponseEntity<Void> reorder(@RequestBody ReorderRequest req) {
        service.reorderTables(req.orderedIds());
        return ResponseEntity.noContent().build();
    }

    public record ReorderRequest(List<String> orderedIds) {}

    public record GenerateRequest(String campaignId, String description, String diceFormula) {}

    public record ImproviseRequest(String campaignId, String tableName, String resultLabel, String resultDetail) {}

    private RandomTableService.TableData toData(RandomTableDTO dto, Integer order) {
        return new RandomTableService.TableData(
                dto.getName(),
                dto.getDescription(),
                dto.getDiceFormula(),
                dto.getIcon(),
                mapper.toDomainEntries(dto.getEntries()),
                dto.getCampaignId(),
                order
        );
    }
}
