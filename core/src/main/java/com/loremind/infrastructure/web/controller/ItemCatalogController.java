package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.ItemCatalogService;
import com.loremind.domain.campaigncontext.itemcatalog.ItemCatalog;
import com.loremind.domain.campaigncontext.ports.exceptions.ItemCatalogGenerationException;
import com.loremind.infrastructure.web.dto.campaigncontext.ItemCatalogDTO;
import com.loremind.infrastructure.web.mapper.ItemCatalogMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/item-catalogs")
public class ItemCatalogController {

    private final ItemCatalogService service;
    private final ItemCatalogMapper mapper;

    public ItemCatalogController(ItemCatalogService service, ItemCatalogMapper mapper) {
        this.service = service;
        this.mapper = mapper;
    }

    @PostMapping
    public ResponseEntity<ItemCatalogDTO> create(@RequestBody ItemCatalogDTO dto) {
        ItemCatalog created = service.createCatalog(toData(dto, null));
        return ResponseEntity.ok(mapper.toDTO(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<ItemCatalogDTO> getById(@PathVariable String id) {
        return service.getCatalogById(id)
                .map(c -> ResponseEntity.ok(mapper.toDTO(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/campaign/{campaignId}")
    public ResponseEntity<List<ItemCatalogDTO>> getByCampaign(@PathVariable String campaignId) {
        List<ItemCatalogDTO> dtos = service.getCatalogsByCampaignId(campaignId).stream()
                .map(mapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PutMapping("/{id}")
    public ResponseEntity<ItemCatalogDTO> update(@PathVariable String id, @RequestBody ItemCatalogDTO dto) {
        ItemCatalog updated = service.updateCatalog(id, toData(dto, dto.getOrder()));
        return ResponseEntity.ok(mapper.toDTO(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        service.deleteCatalog(id);
        return ResponseEntity.noContent().build();
    }

    /** Recherche par nom — alimente la recherche globale (Ctrl+K). */
    @GetMapping("/search")
    public ResponseEntity<List<ItemCatalogDTO>> search(@RequestParam("q") String query) {
        return ResponseEntity.ok(service.searchCatalogs(query).stream()
                .map(mapper::toDTO)
                .collect(java.util.stream.Collectors.toList()));
    }

    /** Génère une PROPOSITION de catalogue via l'IA (non persistée) — l'UI préremplit le formulaire. */
    @PostMapping("/generate")
    public ResponseEntity<ItemCatalogDTO> generate(@RequestBody GenerateRequest req) {
        try {
            ItemCatalog proposal = service.generateProposal(req.campaignId(), req.description());
            return ResponseEntity.ok(mapper.toDTO(proposal));
        } catch (ItemCatalogGenerationException e) {
            throw new ResponseStatusException(HttpStatus.BAD_GATEWAY, e.getMessage(), e);
        }
    }

    private ItemCatalogService.CatalogData toData(ItemCatalogDTO dto, Integer order) {
        return new ItemCatalogService.CatalogData(
                dto.getName(),
                dto.getDescription(),
                dto.getIcon(),
                mapper.toDomainItems(dto.getItems()),
                dto.getCampaignId(),
                order
        );
    }

    public record GenerateRequest(String campaignId, String description) {}
}
