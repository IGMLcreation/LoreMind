package com.loremind.application.lorecontext;

import com.loremind.domain.lorecontext.Template;
import com.loremind.domain.shared.template.TemplateField;
import com.loremind.domain.lorecontext.ports.TemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test unitaire pour TemplateService.
 * Vérifie le CRUD, la copie défensive des champs en create/update, le
 * comportement null-safe de fields, et l'immuabilité de loreId.
 */
@ExtendWith(MockitoExtension.class)
public class TemplateServiceTest {

    @Mock private TemplateRepository templateRepository;

    @InjectMocks private TemplateService templateService;

    private Template existing;
    private List<TemplateField> originalFields;

    @BeforeEach
    void setUp() {
        originalFields = List.of(TemplateField.text("Histoire"), TemplateField.image("Portrait"));
        existing = Template.builder()
                .id("tpl-1").loreId("lore-1").name("Personnage")
                .description("desc").defaultNodeId("n-1")
                .fields(List.of(TemplateField.text("Old")))
                .build();
    }

    @Test
    void testCreateTemplate_CopiesFieldsDefensively() {
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));

        templateService.createTemplate("lore-1", "T", "d", "n-1", originalFields);

        ArgumentCaptor<Template> captor = ArgumentCaptor.forClass(Template.class);
        verify(templateRepository).save(captor.capture());
        Template saved = captor.getValue();
        assertEquals("T", saved.getName());
        assertEquals(2, saved.getFields().size());
        assertNotSame(originalFields, saved.getFields()); // copie defensive
    }

    @Test
    void testCreateTemplate_NullFieldsBecomesEmptyList() {
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));

        templateService.createTemplate("lore-1", "T", "d", "n-1", null);

        ArgumentCaptor<Template> captor = ArgumentCaptor.forClass(Template.class);
        verify(templateRepository).save(captor.capture());
        assertNotNull(captor.getValue().getFields());
        assertTrue(captor.getValue().getFields().isEmpty());
    }

    @Test
    void testGetTemplateById_AndByLoreId_AndAll() {
        when(templateRepository.findById("tpl-1")).thenReturn(Optional.of(existing));
        when(templateRepository.findAll()).thenReturn(List.of(existing));
        when(templateRepository.findByLoreId("lore-1")).thenReturn(List.of(existing));

        assertTrue(templateService.getTemplateById("tpl-1").isPresent());
        assertEquals(1, templateService.getAllTemplates().size());
        assertEquals(1, templateService.getTemplatesByLoreId("lore-1").size());
    }

    @Test
    void testSearch_NullOrBlankReturnsEmpty() {
        assertTrue(templateService.searchTemplates(null).isEmpty());
        assertTrue(templateService.searchTemplates("  ").isEmpty());
        verifyNoInteractions(templateRepository);
    }

    @Test
    void testSearch_TrimsQuery() {
        when(templateRepository.searchByName("perso")).thenReturn(List.of(existing));

        templateService.searchTemplates("  perso  ");

        verify(templateRepository).searchByName("perso");
    }

    @Test
    void testUpdateTemplate_AppliesChangesKeepsLoreId() {
        Template changes = Template.builder()
                .loreId("lore-OTHER") // doit etre ignore
                .name("Nouveau").description("nd").defaultNodeId("n-2")
                .fields(originalFields)
                .build();
        when(templateRepository.findById("tpl-1")).thenReturn(Optional.of(existing));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));

        Template result = templateService.updateTemplate("tpl-1", changes);

        assertEquals("Nouveau", result.getName());
        assertEquals("nd", result.getDescription());
        assertEquals("n-2", result.getDefaultNodeId());
        assertEquals(2, result.getFields().size());
        assertNotSame(originalFields, result.getFields()); // copie defensive
        // loreId immuable
        assertEquals("lore-1", result.getLoreId());
    }

    @Test
    void testUpdateTemplate_NullFieldsBecomesEmpty() {
        Template changes = Template.builder().name("N").description("d")
                .defaultNodeId("n-1").fields(null).build();
        when(templateRepository.findById("tpl-1")).thenReturn(Optional.of(existing));
        when(templateRepository.save(any(Template.class))).thenAnswer(inv -> inv.getArgument(0));

        Template result = templateService.updateTemplate("tpl-1", changes);

        assertNotNull(result.getFields());
        assertTrue(result.getFields().isEmpty());
    }

    @Test
    void testUpdateTemplate_NotFoundThrows() {
        when(templateRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> templateService.updateTemplate("missing", existing));
    }

    @Test
    void testDelete() {
        templateService.deleteTemplate("tpl-1");
        verify(templateRepository).deleteById("tpl-1");
    }
}
