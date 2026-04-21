package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.lorecontext.Lore;
import com.loremind.domain.lorecontext.ports.LoreRepository;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test pour vérifier que PostgresLoreRepository fonctionne correctement.
 * Utilise PostgreSQL (loremind_test) pour les tests d'intégration.
 */
@SpringBootTest
public class PostgresLoreRepositoryTest {

    @Autowired
    private LoreRepository loreRepository;

    @Test
    public void testSaveAndFindLore() {
        // Créer un Lore
        Lore lore = Lore.builder()
                .name("Lore Test")
                .description("Description test")
                .nodeCount(0)
                .pageCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        // Sauvegarder
        Lore savedLore = loreRepository.save(lore);
        assertNotNull(savedLore.getId());

        // Récupérer
        Optional<Lore> foundLore = loreRepository.findById(savedLore.getId());
        assertTrue(foundLore.isPresent());
        assertEquals("Lore Test", foundLore.get().getName());

        // Nettoyer
        loreRepository.deleteById(savedLore.getId());
    }

    @Test
    public void testFindAllLores() {
        // Créer deux Lores
        Lore lore1 = Lore.builder()
                .name("Lore 1")
                .description("Description 1")
                .nodeCount(0)
                .pageCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Lore lore2 = Lore.builder()
                .name("Lore 2")
                .description("Description 2")
                .nodeCount(0)
                .pageCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Lore saved1 = loreRepository.save(lore1);
        Lore saved2 = loreRepository.save(lore2);

        // Récupérer tous
        List<Lore> allLores = loreRepository.findAll();
        assertTrue(allLores.size() >= 2);

        // Nettoyer
        loreRepository.deleteById(saved1.getId());
        loreRepository.deleteById(saved2.getId());
    }

    @Test
    public void testDeleteLore() {
        // Créer un Lore
        Lore lore = Lore.builder()
                .name("Lore to delete")
                .description("Description")
                .nodeCount(0)
                .pageCount(0)
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        Lore savedLore = loreRepository.save(lore);

        // Supprimer
        loreRepository.deleteById(savedLore.getId());

        // Vérifier qu'il n'existe plus
        assertFalse(loreRepository.existsById(savedLore.getId()));
    }
}
