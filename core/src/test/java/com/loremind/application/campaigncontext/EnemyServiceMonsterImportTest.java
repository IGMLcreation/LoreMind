package com.loremind.application.campaigncontext;

import com.loremind.infrastructure.persistence.entity.CampaignJpaEntity;
import com.loremind.infrastructure.persistence.jpa.CampaignJpaRepository;
import com.loremind.infrastructure.persistence.jpa.EnemyJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Import de monstres Foundry dans le bestiaire : upsert (dédup par foundryRef).
 */
@SpringBootTest
@Transactional
class EnemyServiceMonsterImportTest {

    @Autowired private EnemyService enemyService;
    @Autowired private CampaignJpaRepository campaignRepo;
    @Autowired private EnemyJpaRepository enemyRepo;

    @Test
    void importFoundryMonsters_createsThenUpsertsByFoundryRef() {
        Long cid = campaignRepo.save(
                CampaignJpaEntity.builder().name("Camp").arcsCount(0).build()).getId();

        var r1 = enemyService.importFoundryMonsters(String.valueOf(cid), List.of(
                new EnemyService.MonsterImport("Goblin", "Compendium.nimble.monsters.Actor.g1", Map.of("level", "1"), "Briarban", null),
                new EnemyService.MonsterImport("Orc", "Compendium.nimble.monsters.Actor.o1", Map.of(), null, null)));
        assertEquals(2, r1.created());
        assertEquals(0, r1.updated());
        assertEquals(2, enemyRepo.findByCampaignIdOrderByOrderAsc(cid).size());

        // Snapshot de stats conservé + arborescence Foundry sous "Foundry/".
        var goblin = enemyRepo.findByCampaignIdOrderByOrderAsc(cid).stream()
                .filter(e -> "Compendium.nimble.monsters.Actor.g1".equals(e.getFoundryRef()))
                .findFirst().orElseThrow();
        assertEquals("1", goblin.getFoundryStats().get("level"));
        assertEquals("Foundry/Briarban", goblin.getFolder());
        var orc = enemyRepo.findByCampaignIdOrderByOrderAsc(cid).stream()
                .filter(e -> "Compendium.nimble.monsters.Actor.o1".equals(e.getFoundryRef()))
                .findFirst().orElseThrow();
        assertEquals("Foundry", orc.getFolder()); // sans dossier Foundry -> racine

        // Réimport : g1 déjà connu (renommé + redossiérisé) + un nouveau (k1). Pas de doublon.
        var r2 = enemyService.importFoundryMonsters(String.valueOf(cid), List.of(
                new EnemyService.MonsterImport("Goblin Boss", "Compendium.nimble.monsters.Actor.g1", Map.of(), "Briarban/Bosses", null),
                new EnemyService.MonsterImport("Kobold", "Compendium.nimble.monsters.Actor.k1", Map.of(), "Kobolds", null)));
        assertEquals(1, r2.created());
        assertEquals(1, r2.updated());
        assertEquals("Foundry/Briarban/Bosses", enemyRepo.findByCampaignIdOrderByOrderAsc(cid).stream()
                .filter(e -> "Compendium.nimble.monsters.Actor.g1".equals(e.getFoundryRef()))
                .findFirst().orElseThrow().getFolder());

        var all = enemyRepo.findByCampaignIdOrderByOrderAsc(cid);
        assertEquals(3, all.size());
        assertTrue(all.stream().anyMatch(e ->
                "Goblin Boss".equals(e.getName())
                        && "Compendium.nimble.monsters.Actor.g1".equals(e.getFoundryRef())));
    }

    @Test
    void importFoundryMonsters_ignoresBlankRefOrName() {
        Long cid = campaignRepo.save(
                CampaignJpaEntity.builder().name("Camp2").arcsCount(0).build()).getId();

        var r = enemyService.importFoundryMonsters(String.valueOf(cid), List.of(
                new EnemyService.MonsterImport("", "Compendium.x.Actor.a", Map.of(), null, null),
                new EnemyService.MonsterImport("SansRef", " ", Map.of(), null, null),
                new EnemyService.MonsterImport("Valide", "Compendium.x.Actor.b", Map.of(), null, null)));
        assertEquals(1, r.created());
        assertEquals(1, enemyRepo.findByCampaignIdOrderByOrderAsc(cid).size());
    }
}
