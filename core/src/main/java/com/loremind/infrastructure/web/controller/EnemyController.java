package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.EnemyService;
import com.loremind.domain.campaigncontext.Enemy;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Map;

/**
 * REST Controller des fiches d'ennemis (bestiaire de campagne).
 * Réponses = domaine {@link Enemy} sérialisé tel quel (Lombok @Data) ;
 * requêtes = record dédié (le domaine n'a pas de constructeur no-args).
 */
@RestController
@RequestMapping("/api/enemies")
public class EnemyController {

    private final EnemyService enemyService;

    public EnemyController(EnemyService enemyService) {
        this.enemyService = enemyService;
    }

    @PostMapping
    public ResponseEntity<Enemy> create(@RequestBody EnemyRequest req) {
        return ResponseEntity.ok(enemyService.createEnemy(toData(req)));
    }

    @GetMapping("/{id}")
    public ResponseEntity<Enemy> getById(@PathVariable String id) {
        return enemyService.getEnemyById(id)
                .map(ResponseEntity::ok)
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/campaign/{campaignId}")
    public ResponseEntity<List<Enemy>> getByCampaign(@PathVariable String campaignId) {
        return ResponseEntity.ok(enemyService.getEnemiesByCampaignId(campaignId));
    }

    /** Recherche par nom — alimente la recherche globale (Ctrl+K). */
    @GetMapping("/search")
    public ResponseEntity<List<Enemy>> search(@RequestParam("q") String query) {
        return ResponseEntity.ok(enemyService.searchEnemies(query));
    }

    @PutMapping("/{id}")
    public ResponseEntity<Enemy> update(@PathVariable String id, @RequestBody EnemyRequest req) {
        return ResponseEntity.ok(enemyService.updateEnemy(id, toData(req)));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        enemyService.deleteEnemy(id);
        return ResponseEntity.noContent().build();
    }

    /** Réordonne (et reclasse) les ennemis d'un dossier : order = position. */
    @PutMapping("/reorder")
    public ResponseEntity<Void> reorder(@RequestBody ReorderRequest req) {
        enemyService.reorderEnemies(req.folder(), req.orderedIds());
        return ResponseEntity.noContent().build();
    }

    public record ReorderRequest(String folder, List<String> orderedIds) {}

    private EnemyService.EnemyData toData(EnemyRequest req) {
        return new EnemyService.EnemyData(
                req.name(), req.level(), req.folder(),
                req.portraitImageId(), req.headerImageId(),
                req.values(), req.imageValues(), req.keyValueValues(),
                req.campaignId(), req.order());
    }

    public record EnemyRequest(
            String name,
            String level,
            String folder,
            String portraitImageId,
            String headerImageId,
            Map<String, String> values,
            Map<String, List<String>> imageValues,
            Map<String, Map<String, String>> keyValueValues,
            String campaignId,
            Integer order) {}
}
