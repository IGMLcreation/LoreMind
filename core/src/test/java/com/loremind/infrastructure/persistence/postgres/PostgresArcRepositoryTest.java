package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
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
 * Tests d'integration pour PostgresArcRepository.
 * Valide la persistance des 3 collections JSONB (relatedPageIds,
 * illustrationImageIds, mapImageIds) et des 5 champs narratifs enrichis.
 */
@SpringBootTest
@Transactional
class PostgresArcRepositoryTest {

    @Autowired private ArcRepository repository;
    @Autowired private CampaignRepository campaignRepository;

    private String campaignId;

    @BeforeEach
    void setUp() {
        campaignId = campaignRepository.save(
                Campaign.builder().name("Camp").description("").build()).getId();
    }

    @Test
    void save_arcWithAllFields_roundTrips() {
        Arc arc = Arc.builder()
                .campaignId(campaignId).name("Acte I").description("Mise en place").order(0)
                .themes("trahison").stakes("survie").gmNotes("secret").rewards("artefact").resolution("couronnement")
                .relatedPageIds(List.of("page-1"))
                .illustrationImageIds(List.of("img-a", "img-b"))
                .mapImageIds(List.of("map-1"))
                .build();

        Arc saved = repository.save(arc);
        assertNotNull(saved.getId());

        Arc r = repository.findById(saved.getId()).orElseThrow();
        assertEquals("Acte I", r.getName());
        assertEquals("trahison", r.getThemes());
        assertEquals("secret", r.getGmNotes());
        assertEquals(List.of("page-1"), r.getRelatedPageIds());
        assertEquals(2, r.getIllustrationImageIds().size());
        assertEquals(List.of("map-1"), r.getMapImageIds());
    }

    @Test
    void findByCampaignId_returnsArcsOfThatCampaign() {
        String otherCamp = campaignRepository.save(
                Campaign.builder().name("Other").description("").build()).getId();
        repository.save(Arc.builder().campaignId(campaignId).name("A").order(0).build());
        repository.save(Arc.builder().campaignId(campaignId).name("B").order(1).build());
        repository.save(Arc.builder().campaignId(otherCamp).name("C").order(0).build());

        assertEquals(2, repository.findByCampaignId(campaignId).size());
    }

    @Test
    void deleteById_removesArc() {
        Arc saved = repository.save(Arc.builder().campaignId(campaignId).name("X").order(0).build());
        repository.deleteById(saved.getId());
        assertFalse(repository.existsById(saved.getId()));
    }

    @Test
    void save_emptyCollections_roundTripAsEmpty() {
        Arc arc = Arc.builder().campaignId(campaignId).name("Minimal").order(0).build();
        Arc saved = repository.save(arc);

        Arc r = repository.findById(saved.getId()).orElseThrow();
        assertNotNull(r.getRelatedPageIds());
        assertTrue(r.getRelatedPageIds().isEmpty());
        assertTrue(r.getIllustrationImageIds().isEmpty());
        assertTrue(r.getMapImageIds().isEmpty());
    }
}
