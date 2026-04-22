package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.Scene;
import com.loremind.domain.campaigncontext.SceneBranch;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests d'integration pour PostgresSceneRepository.
 * Valide particulierement la persistance de la liste SceneBranch (JSONB avec
 * Value Object immuable + @Jacksonized).
 */
@SpringBootTest
@Transactional
class PostgresSceneRepositoryTest {

    @Autowired private SceneRepository repository;
    @Autowired private ChapterRepository chapterRepository;
    @Autowired private ArcRepository arcRepository;
    @Autowired private CampaignRepository campaignRepository;

    private String chapterId;

    @BeforeEach
    void setUp() {
        String campaignId = campaignRepository.save(Campaign.builder().name("C").description("").build()).getId();
        String arcId = arcRepository.save(Arc.builder().campaignId(campaignId).name("A").order(0).build()).getId();
        chapterId = chapterRepository.save(Chapter.builder().arcId(arcId).name("Ch").order(0).build()).getId();
    }

    @Test
    void save_sceneWithAllFields_roundTrips() {
        Scene scene = Scene.builder()
                .chapterId(chapterId).name("L'auberge").description("Rencontre tendue").order(0)
                .location("Taverne du Dragon d'Or").timing("Soir").atmosphere("fumee, rires")
                .playerNarration("Vous entrez...").gmSecretNotes("Piege cache")
                .choicesConsequences("Si attaque -> gardes").combatDifficulty("facile").enemies("3 brigands")
                .relatedPageIds(List.of("page-aubergiste"))
                .illustrationImageIds(List.of("img-1", "img-2"))
                .mapImageIds(List.of("plan-taverne"))
                .build();

        Scene saved = repository.save(scene);
        assertNotNull(saved.getId());

        Scene r = repository.findById(saved.getId()).orElseThrow();
        assertEquals("L'auberge", r.getName());
        assertEquals("Taverne du Dragon d'Or", r.getLocation());
        assertEquals("Piege cache", r.getGmSecretNotes());
        assertEquals(2, r.getIllustrationImageIds().size());
        assertEquals(List.of("plan-taverne"), r.getMapImageIds());
    }

    @Test
    void save_scenePreservesBranches_viaJsonbRoundTrip() {
        // Le critique : le @Jacksonized de SceneBranch doit permettre la
        // reconstruction via builder apres serialisation Jackson.
        Scene scene = Scene.builder()
                .chapterId(chapterId).name("Decision").order(0)
                .branches(List.of(
                        SceneBranch.builder().label("fuite").targetSceneId("sc-2").condition("HP bas").build(),
                        SceneBranch.builder().label("combat").targetSceneId("sc-3").build()
                ))
                .build();

        Scene saved = repository.save(scene);
        Scene r = repository.findById(saved.getId()).orElseThrow();

        assertEquals(2, r.getBranches().size());
        assertEquals("fuite", r.getBranches().get(0).getLabel());
        assertEquals("sc-2", r.getBranches().get(0).getTargetSceneId());
        assertEquals("HP bas", r.getBranches().get(0).getCondition());
        assertEquals("combat", r.getBranches().get(1).getLabel());
    }

    @Test
    void findByChapterId_returnsScenesOfThatChapter() {
        String campaignId = campaignRepository.save(Campaign.builder().name("C2").description("").build()).getId();
        String arcId = arcRepository.save(Arc.builder().campaignId(campaignId).name("A2").order(0).build()).getId();
        String otherChapter = chapterRepository.save(Chapter.builder().arcId(arcId).name("Ch2").order(0).build()).getId();

        repository.save(Scene.builder().chapterId(chapterId).name("A").order(0).build());
        repository.save(Scene.builder().chapterId(chapterId).name("B").order(1).build());
        repository.save(Scene.builder().chapterId(otherChapter).name("C").order(0).build());

        assertEquals(2, repository.findByChapterId(chapterId).size());
    }

    @Test
    void deleteById_removesScene() {
        Scene saved = repository.save(Scene.builder().chapterId(chapterId).name("X").order(0).build());
        assertTrue(repository.existsById(saved.getId()));
        repository.deleteById(saved.getId());
        assertFalse(repository.existsById(saved.getId()));
    }
}
