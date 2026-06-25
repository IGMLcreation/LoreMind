package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.EnemyService;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/**
 * Import dans le bestiaire d'une campagne d'un catalogue de monstres exporté depuis
 * Foundry (compendiums). On ne stocke que nom + référence (UUID de compendium) :
 * les stats restent côté Foundry et sont ré-instanciées à l'export.
 *
 * {@code POST /api/campaigns/{campaignId}/import-foundry-monsters}
 */
@RestController
@RequestMapping("/api/campaigns/{campaignId}")
public class FoundryMonsterImportController {

    private final EnemyService enemyService;

    public FoundryMonsterImportController(EnemyService enemyService) {
        this.enemyService = enemyService;
    }

    /** Format du catalogue produit par le module Foundry. */
    public record MonsterCatalog(String system, List<MonsterEntry> monsters) {}

    public record MonsterEntry(String name, String uuid, String img) {}

    @PostMapping("/import-foundry-monsters")
    public ResponseEntity<EnemyService.MonsterImportResult> importMonsters(
            @PathVariable String campaignId,
            @RequestBody MonsterCatalog catalog) {
        List<EnemyService.MonsterImport> monsters = (catalog.monsters() == null ? List.<MonsterEntry>of() : catalog.monsters())
                .stream()
                .map(m -> new EnemyService.MonsterImport(m.name(), m.uuid()))
                .toList();
        return ResponseEntity.ok(enemyService.importFoundryMonsters(campaignId, monsters));
    }
}
