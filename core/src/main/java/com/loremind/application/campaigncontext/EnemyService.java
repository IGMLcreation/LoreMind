package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Enemy;
import com.loremind.domain.campaigncontext.ports.EnemyRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service d'application pour les fiches d'ennemis (bestiaire de campagne).
 * Miroir de {@link NpcService} : fiche pilotée par le template ENNEMI du GameSystem.
 */
@Service
public class EnemyService {

    private final EnemyRepository enemyRepository;

    public EnemyService(EnemyRepository enemyRepository) {
        this.enemyRepository = enemyRepository;
    }

    public record EnemyData(
            String name,
            String level,
            String folder,
            String portraitImageId,
            String headerImageId,
            Map<String, String> values,
            Map<String, List<String>> imageValues,
            Map<String, Map<String, String>> keyValueValues,
            String campaignId,
            Integer order
    ) {}

    public Enemy createEnemy(EnemyData data) {
        int order = data.order() != null ? data.order() : nextOrderFor(data.campaignId());
        Enemy enemy = Enemy.builder()
                .name(data.name())
                .level(normalize(data.level()))
                .folder(normalize(data.folder()))
                .portraitImageId(data.portraitImageId())
                .headerImageId(data.headerImageId())
                .values(data.values() != null ? new HashMap<>(data.values()) : new HashMap<>())
                .imageValues(data.imageValues() != null ? new HashMap<>(data.imageValues()) : new HashMap<>())
                .keyValueValues(data.keyValueValues() != null ? new HashMap<>(data.keyValueValues()) : new HashMap<>())
                .campaignId(data.campaignId())
                .order(order)
                .build();
        return enemyRepository.save(enemy);
    }

    public Optional<Enemy> getEnemyById(String id) {
        return enemyRepository.findById(id);
    }

    public List<Enemy> getEnemiesByCampaignId(String campaignId) {
        return enemyRepository.findByCampaignId(campaignId);
    }

    public Enemy updateEnemy(String id, EnemyData data) {
        Enemy existing = enemyRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Enemy non trouvé avec l'ID: " + id));
        existing.setName(data.name());
        existing.setLevel(normalize(data.level()));
        existing.setFolder(normalize(data.folder()));
        existing.setPortraitImageId(data.portraitImageId());
        existing.setHeaderImageId(data.headerImageId());
        existing.setValues(data.values() != null ? new HashMap<>(data.values()) : new HashMap<>());
        existing.setImageValues(data.imageValues() != null ? new HashMap<>(data.imageValues()) : new HashMap<>());
        existing.setKeyValueValues(data.keyValueValues() != null ? new HashMap<>(data.keyValueValues()) : new HashMap<>());
        if (data.order() != null) {
            existing.setOrder(data.order());
        }
        return enemyRepository.save(existing);
    }

    public void deleteEnemy(String id) {
        enemyRepository.deleteById(id);
    }

    // --- Import de monstres depuis un compendium Foundry -------------------

    /** Un monstre du catalogue Foundry : nom + référence + snapshot de stats + dossier. */
    public record MonsterImport(String name, String foundryRef, Map<String, String> stats, String folder) {}

    /**
     * Dossier LoreMind d'un monstre importé : on conserve l'arborescence Foundry
     * sous un dossier racine « Foundry » ("Foundry/Briarban", "Foundry" si aucun).
     */
    private static String foundryFolder(String path) {
        String p = path == null ? "" : path.trim();
        return p.isEmpty() ? "Foundry" : "Foundry/" + p;
    }

    public record MonsterImportResult(int created, int updated) {}

    /**
     * Importe (upsert) des monstres Foundry dans le bestiaire d'une campagne.
     * Dédup par {@code foundryRef} : un monstre déjà importé est mis à jour (nom),
     * jamais dupliqué. Fiche minimale (nom + référence) ; les stats restent côté
     * Foundry et sont ré-instanciées à l'export.
     */
    public MonsterImportResult importFoundryMonsters(String campaignId, List<MonsterImport> monsters) {
        List<Enemy> existing = enemyRepository.findByCampaignId(campaignId);
        Map<String, Enemy> byRef = new HashMap<>();
        for (Enemy e : existing) {
            if (e.getFoundryRef() != null) byRef.put(e.getFoundryRef(), e);
        }
        int order = existing.stream().mapToInt(Enemy::getOrder).max().orElse(-1) + 1;

        int created = 0, updated = 0;
        for (MonsterImport m : monsters) {
            if (m.foundryRef() == null || m.foundryRef().isBlank()
                    || m.name() == null || m.name().isBlank()) {
                continue;
            }
            Map<String, String> stats = m.stats() != null ? new HashMap<>(m.stats()) : new HashMap<>();
            String folder = foundryFolder(m.folder());
            Enemy ex = byRef.get(m.foundryRef());
            if (ex != null) {
                ex.setName(m.name());
                ex.setFoundryStats(stats); // rafraîchit le snapshot
                ex.setFolder(folder);      // ré-aligne sur l'arborescence Foundry
                enemyRepository.save(ex);
                updated++;
            } else {
                Enemy saved = enemyRepository.save(Enemy.builder()
                        .name(m.name())
                        .foundryRef(m.foundryRef())
                        .foundryStats(stats)
                        .folder(folder)
                        .campaignId(campaignId)
                        .order(order++)
                        .values(new HashMap<>())
                        .imageValues(new HashMap<>())
                        .keyValueValues(new HashMap<>())
                        .build());
                byRef.put(m.foundryRef(), saved);
                created++;
            }
        }
        return new MonsterImportResult(created, updated);
    }

    public List<Enemy> searchEnemies(String query) {
        if (query == null || query.isBlank()) return List.of();
        return enemyRepository.searchByName(query.trim());
    }

    /** Trim ; chaîne vide → null (= non renseigné / non classé). */
    private static String normalize(String value) {
        if (value == null) return null;
        String trimmed = value.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    private int nextOrderFor(String campaignId) {
        return enemyRepository.findByCampaignId(campaignId).stream()
                .mapToInt(Enemy::getOrder)
                .max()
                .orElse(-1) + 1;
    }
}
