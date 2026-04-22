package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
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

@SpringBootTest
@Transactional
class PostgresChapterRepositoryTest {

    @Autowired private ChapterRepository repository;
    @Autowired private ArcRepository arcRepository;
    @Autowired private CampaignRepository campaignRepository;

    private String arcId;

    @BeforeEach
    void setUp() {
        String campaignId = campaignRepository.save(
                Campaign.builder().name("Camp").description("").build()).getId();
        arcId = arcRepository.save(Arc.builder().campaignId(campaignId).name("Arc").order(0).build()).getId();
    }

    @Test
    void save_chapterWithAllFields_roundTrips() {
        Chapter chapter = Chapter.builder()
                .arcId(arcId).name("L'arrivee").description("Les PJ decouvrent la ville").order(0)
                .gmNotes("note secrete").playerObjectives("trouver l'indice").narrativeStakes("si echec allie meurt")
                .relatedPageIds(List.of("page-x"))
                .illustrationImageIds(List.of("img-1"))
                .mapImageIds(List.of("map-donjon"))
                .build();

        Chapter saved = repository.save(chapter);
        assertNotNull(saved.getId());

        Chapter r = repository.findById(saved.getId()).orElseThrow();
        assertEquals("L'arrivee", r.getName());
        assertEquals("note secrete", r.getGmNotes());
        assertEquals("trouver l'indice", r.getPlayerObjectives());
        assertEquals(List.of("page-x"), r.getRelatedPageIds());
        assertEquals(List.of("map-donjon"), r.getMapImageIds());
    }

    @Test
    void findByArcId_returnsChaptersOfThatArc() {
        String campaignId = campaignRepository.save(
                Campaign.builder().name("Camp2").description("").build()).getId();
        String otherArc = arcRepository.save(Arc.builder().campaignId(campaignId).name("A2").order(0).build()).getId();
        repository.save(Chapter.builder().arcId(arcId).name("Ch1").order(0).build());
        repository.save(Chapter.builder().arcId(arcId).name("Ch2").order(1).build());
        repository.save(Chapter.builder().arcId(otherArc).name("Ch3").order(0).build());

        assertEquals(2, repository.findByArcId(arcId).size());
    }

    @Test
    void deleteById_removesChapter() {
        Chapter saved = repository.save(Chapter.builder().arcId(arcId).name("X").order(0).build());
        assertTrue(repository.existsById(saved.getId()));
        repository.deleteById(saved.getId());
        assertFalse(repository.existsById(saved.getId()));
    }
}
