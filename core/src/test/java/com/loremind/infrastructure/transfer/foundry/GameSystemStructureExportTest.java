package com.loremind.infrastructure.transfer.foundry;

import com.loremind.application.gamesystemcontext.GameSystemService;
import com.loremind.domain.gamesystemcontext.GameSystem;
import com.loremind.domain.shared.template.FieldType;
import com.loremind.infrastructure.persistence.entity.*;
import com.loremind.infrastructure.persistence.jpa.*;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Pont « structure système Foundry » : import d'une structure -> template ennemi
 * mappé (foundryPath) + type d'acteur, puis export d'un ennemi MAISON en acteur typé
 * ({@code system.<chemin> = valeur}).
 */
@SpringBootTest
@Transactional
class GameSystemStructureExportTest {

    @Autowired private GameSystemService gameSystemService;
    @Autowired private GameSystemJpaRepository gameSystemRepo;
    @Autowired private FoundryExportService exportService;
    @Autowired private CampaignJpaRepository campaignRepo;
    @Autowired private EnemyJpaRepository enemyRepo;

    @Test
    void importStructureThenExportTypedActor() {
        GameSystemJpaEntity gs = gameSystemRepo.save(
                GameSystemJpaEntity.builder().name("Nimble-like").isPublic(false).build());

        // Import de structure : 2 champs scalaires mappés + type d'acteur "npc".
        GameSystem updated = gameSystemService.importFoundryStructure(String.valueOf(gs.getId()), "npc", List.of(
                new GameSystemService.FoundryStructField("attributes.hp.value", "PV", "number"),
                new GameSystemService.FoundryStructField("abilities.str.value", "FOR", "number")));

        assertEquals("npc", updated.getFoundryActorType());
        assertEquals(2, updated.getEnemyTemplate().size());
        var pv = updated.getEnemyTemplate().stream().filter(f -> "PV".equals(f.getName())).findFirst().orElseThrow();
        assertEquals("attributes.hp.value", pv.getFoundryPath());
        assertEquals(FieldType.NUMBER, pv.getType());

        // Campagne adossée au système + un ennemi MAISON (sans référence) rempli.
        CampaignJpaEntity camp = campaignRepo.save(CampaignJpaEntity.builder()
                .name("Camp").arcsCount(0).gameSystemId(String.valueOf(gs.getId())).build());
        enemyRepo.save(EnemyJpaEntity.builder()
                .campaignId(camp.getId()).name("Bandit maison").order(0)
                .values(Map.of("PV", "11", "FOR", "3"))
                .build());

        FoundryExportService.BuiltBundle bundle =
                exportService.buildBundle(String.valueOf(camp.getId()), "2026-06-25T00:00:00Z");

        var enemy = bundle.data().enemies().stream()
                .filter(e -> "Bandit maison".equals(e.name())).findFirst().orElseThrow();
        assertNotNull(enemy.foundryActor(), "un ennemi maison mappé doit porter un foundryActor");
        assertEquals("npc", enemy.foundryActor().type());

        // system imbriqué : attributes.hp.value = 11, abilities.str.value = 3.
        Map<String, Object> system = enemy.foundryActor().system();
        assertEquals(11, nested(system, "attributes", "hp", "value"));
        assertEquals(3, nested(system, "abilities", "str", "value"));
    }

    @SuppressWarnings("unchecked")
    private static Object nested(Map<String, Object> root, String... path) {
        Object cur = root;
        for (String p : path) cur = ((Map<String, Object>) cur).get(p);
        return cur;
    }
}
