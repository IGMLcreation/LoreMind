package com.loremind.infrastructure.web.controller;

import com.loremind.application.lorecontext.LoreService;
import com.loremind.domain.lorecontext.Lore;
import com.loremind.infrastructure.web.dto.lorecontext.LoreDTO;
import com.loremind.infrastructure.web.mapper.LoreMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller pour le contexte Lore.
 * Adaptateur d'infrastructure qui expose l'API REST.
 * Utilise le Service d'application et le Mapper selon l'Architecture Hexagonale.
 */
@RestController
@RequestMapping("/api/lores")
public class LoreController {

    private final LoreService loreService;
    private final LoreMapper loreMapper;

    public LoreController(LoreService loreService, LoreMapper loreMapper) {
        this.loreService = loreService;
        this.loreMapper = loreMapper;
    }

    @PostMapping
    public ResponseEntity<LoreDTO> createLore(@RequestBody LoreDTO loreDTO) {
        Lore lore = loreMapper.toDomain(loreDTO);
        Lore createdLore = loreService.createLore(lore.getName(), lore.getDescription());
        return ResponseEntity.ok(loreMapper.toDTO(createdLore));
    }

    @GetMapping("/{id}")
    public ResponseEntity<LoreDTO> getLoreById(@PathVariable String id) {
        return loreService.getLoreById(id)
                .map(lore -> ResponseEntity.ok(loreMapper.toDTO(lore)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<LoreDTO>> getAllLores() {
        List<Lore> lores = loreService.getAllLores();
        List<LoreDTO> loreDTOs = lores.stream()
                .map(loreMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(loreDTOs);
    }

    @GetMapping("/search")
    public ResponseEntity<List<LoreDTO>> searchLores(@RequestParam("q") String query) {
        List<LoreDTO> dtos = loreService.searchLores(query).stream()
                .map(loreMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PutMapping("/{id}")
    public ResponseEntity<LoreDTO> updateLore(@PathVariable String id, @RequestBody LoreDTO loreDTO) {
        Lore updatedLore = loreService.updateLore(id, loreDTO.getName(), loreDTO.getDescription());
        return ResponseEntity.ok(loreMapper.toDTO(updatedLore));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteLore(@PathVariable String id) {
        loreService.deleteLore(id);
        return ResponseEntity.noContent().build();
    }

    /**
     * Récapitulatif des entités qui seront supprimées / détachées en cascade.
     * Utilisé par l'UI pour afficher "X dossiers, Y pages, Z templates,
     * N campagne(s) détachée(s)" dans la confirmation.
     */
    @GetMapping("/{id}/deletion-impact")
    public ResponseEntity<LoreService.DeletionImpact> getDeletionImpact(@PathVariable String id) {
        if (loreService.getLoreById(id).isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok(loreService.getDeletionImpact(id));
    }
}
