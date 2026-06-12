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
