package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Tests d'integration pour PostgresCampaignRepository.
 * Valide le champ optionnel {@code loreId} (weak reference cross-context).
 */
@SpringBootTest
@Transactional
class PostgresCampaignRepositoryTest {

    @Autowired private CampaignRepository repository;

    @Test
    void save_campaignWithLoreLink_roundTrips() {
        Campaign c = Campaign.builder()
                .name("Les Ombres").description("Dark fantasy")
                .loreId("lore-123").build();

        Campaign saved = repository.save(c);
        assertNotNull(saved.getId());

        Campaign r = repository.findById(saved.getId()).orElseThrow();
        assertEquals("Les Ombres", r.getName());
        assertEquals("lore-123", r.getLoreId());
        assertTrue(r.isLinkedToLore());
    }

    @Test
    void save_oneShotCampaign_hasNullLoreId() {
        Campaign c = Campaign.builder().name("One-shot").description("").build();
        Campaign saved = repository.save(c);

        Campaign r = repository.findById(saved.getId()).orElseThrow();
        assertNull(r.getLoreId());
        assertFalse(r.isLinkedToLore());
    }

    @Test
    void findAll_returnsAllSavedCampaigns() {
        repository.save(Campaign.builder().name("A").description("").build());
        repository.save(Campaign.builder().name("B").description("").build());

        assertTrue(repository.findAll().size() >= 2);
    }

    @Test
    void searchByName_findsByPartialMatch() {
        repository.save(Campaign.builder().name("Les Ombres d'Ithoril").description("").build());
        repository.save(Campaign.builder().name("La Porte Noire").description("").build());
        repository.save(Campaign.builder().name("Ere du Dragon").description("").build());

        List<Campaign> hits = repository.searchByName("ombres");
        assertTrue(hits.stream().anyMatch(c -> c.getName().contains("Ombres")));
    }

    @Test
    void deleteById_removesCampaign() {
        Campaign saved = repository.save(Campaign.builder().name("X").description("").build());
        assertTrue(repository.existsById(saved.getId()));

        repository.deleteById(saved.getId());

        assertFalse(repository.existsById(saved.getId()));
    }

    @Test
    void save_updatesName_whenSavingExistingCampaign() {
        Campaign saved = repository.save(Campaign.builder().name("old").description("").build());
        saved.setName("new");
        repository.save(saved);

        assertEquals("new", repository.findById(saved.getId()).orElseThrow().getName());
    }
}
