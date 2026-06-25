package com.loremind.application.campaigncontext;

import com.loremind.infrastructure.persistence.entity.CampaignJpaEntity;
import com.loremind.infrastructure.persistence.jpa.CampaignJpaRepository;
import com.loremind.infrastructure.persistence.jpa.EnemyJpaRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

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
                new EnemyService.MonsterImport("Goblin", "Compendium.nimble.monsters.Actor.g1"),
                new EnemyService.MonsterImport("Orc", "Compendium.nimble.monsters.Actor.o1")));
        assertEquals(2, r1.created());
        assertEquals(0, r1.updated());
        assertEquals(2, enemyRepo.findByCampaignIdOrderByOrderAsc(cid).size());

        // Réimport : g1 déjà connu (renommé) + un nouveau (k1). Pas de doublon pour g1.
        var r2 = enemyService.importFoundryMonsters(String.valueOf(cid), List.of(
                new EnemyService.MonsterImport("Goblin Boss", "Compendium.nimble.monsters.Actor.g1"),
                new EnemyService.MonsterImport("Kobold", "Compendium.nimble.monsters.Actor.k1")));
        assertEquals(1, r2.created());
        assertEquals(1, r2.updated());

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
                new EnemyService.MonsterImport("", "Compendium.x.Actor.a"),
                new EnemyService.MonsterImport("SansRef", " "),
                new EnemyService.MonsterImport("Valide", "Compendium.x.Actor.b")));
        assertEquals(1, r.created());
        assertEquals(1, enemyRepo.findByCampaignIdOrderByOrderAsc(cid).size());
    }
}
