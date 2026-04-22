package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.lorecontext.FieldType;
import com.loremind.domain.lorecontext.ImageLayout;
import com.loremind.domain.lorecontext.Lore;
import com.loremind.domain.lorecontext.Template;
import com.loremind.domain.lorecontext.TemplateField;
import com.loremind.domain.lorecontext.ports.LoreRepository;
import com.loremind.domain.lorecontext.ports.TemplateRepository;
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
 * Tests d'integration pour PostgresTemplateRepository.
 * Focus particulier sur la persistance de la liste {@link TemplateField}
 * via le JSONB converter : roundtrip texte+image+layout.
 */
@SpringBootTest
@Transactional
class PostgresTemplateRepositoryTest {

    @Autowired private TemplateRepository repository;
    @Autowired private LoreRepository loreRepository;

    private String loreId;

    @BeforeEach
    void setUp() {
        loreId = loreRepository.save(Lore.builder().name("Lore host").description("").build()).getId();
    }

    @Test
    void save_persistsTemplateWithMixedFields_andRoundTrips() {
        Template tpl = Template.builder()
                .loreId(loreId)
                .name("Fiche PNJ")
                .description("Template de base pour les PNJ")
                .fields(List.of(
                        TemplateField.text("histoire"),
                        TemplateField.image("portraits", ImageLayout.MASONRY),
                        TemplateField.text("motto")
                ))
                .build();

        Template saved = repository.save(tpl);
        assertNotNull(saved.getId());

        Template reloaded = repository.findById(saved.getId()).orElseThrow();
        assertEquals("Fiche PNJ", reloaded.getName());
        assertEquals(3, reloaded.getFields().size());
        assertEquals(FieldType.TEXT, reloaded.getFields().get(0).getType());
        assertEquals(FieldType.IMAGE, reloaded.getFields().get(1).getType());
        assertEquals(ImageLayout.MASONRY, reloaded.getFields().get(1).getLayout());
    }

    @Test
    void findByLoreId_returnsOnlyTemplatesOfThatLore() {
        Lore other = loreRepository.save(Lore.builder().name("Other").description("").build());
        repository.save(Template.builder().loreId(loreId).name("A").fields(List.of()).build());
        repository.save(Template.builder().loreId(loreId).name("B").fields(List.of()).build());
        repository.save(Template.builder().loreId(other.getId()).name("C").fields(List.of()).build());

        assertEquals(2, repository.findByLoreId(loreId).size());
    }

    @Test
    void searchByName_findsMatches() {
        repository.save(Template.builder().loreId(loreId).name("Fiche PNJ").fields(List.of()).build());
        repository.save(Template.builder().loreId(loreId).name("Fiche Lieu").fields(List.of()).build());
        repository.save(Template.builder().loreId(loreId).name("Creature").fields(List.of()).build());

        List<Template> hits = repository.searchByName("fiche");
        assertTrue(hits.size() >= 2);
        assertTrue(hits.stream().allMatch(t -> t.getName().toLowerCase().contains("fiche")));
    }

    @Test
    void deleteById_removesTemplate() {
        Template saved = repository.save(Template.builder().loreId(loreId).name("X").fields(List.of()).build());
        assertTrue(repository.existsById(saved.getId()));

        repository.deleteById(saved.getId());

        assertFalse(repository.existsById(saved.getId()));
    }

    @Test
    void save_updatesExistingTemplate_andPreservesId() {
        Template saved = repository.save(Template.builder().loreId(loreId).name("old").fields(List.of()).build());
        String id = saved.getId();
        saved.setName("new");
        saved.setFields(List.of(TemplateField.text("champ")));
        repository.save(saved);

        Template reloaded = repository.findById(id).orElseThrow();
        assertEquals("new", reloaded.getName());
        assertEquals(1, reloaded.getFields().size());
    }
}
