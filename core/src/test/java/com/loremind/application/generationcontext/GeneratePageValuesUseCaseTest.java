package com.loremind.application.generationcontext;

import com.loremind.domain.generationcontext.GenerationContext;
import com.loremind.domain.generationcontext.GenerationResult;
import com.loremind.domain.generationcontext.ports.AiProvider;
import com.loremind.domain.lorecontext.FieldType;
import com.loremind.domain.lorecontext.Lore;
import com.loremind.domain.lorecontext.LoreNode;
import com.loremind.domain.lorecontext.Page;
import com.loremind.domain.lorecontext.Template;
import com.loremind.domain.lorecontext.TemplateField;
import com.loremind.domain.lorecontext.ports.LoreNodeRepository;
import com.loremind.domain.lorecontext.ports.LoreRepository;
import com.loremind.domain.lorecontext.ports.PageRepository;
import com.loremind.domain.lorecontext.ports.TemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Map;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/**
 * Test unitaire pour GeneratePageValuesUseCase.
 * Couvre : le happy path (contexte IA correctement assemblé avec uniquement
 * les champs TEXT), les erreurs d'intégrité (Page/Template/Lore/Folder
 * introuvables), et la validation métier (template sans champ texte).
 */
@ExtendWith(MockitoExtension.class)
public class GeneratePageValuesUseCaseTest {

    @Mock private PageRepository pageRepository;
    @Mock private TemplateRepository templateRepository;
    @Mock private LoreRepository loreRepository;
    @Mock private LoreNodeRepository loreNodeRepository;
    @Mock private AiProvider aiProvider;

    @InjectMocks private GeneratePageValuesUseCase useCase;

    private Page page;
    private Template template;
    private Lore lore;
    private LoreNode folder;

    @BeforeEach
    void setUp() {
        page = Page.builder()
                .id("p-1").loreId("lore-1").nodeId("node-1").templateId("tpl-1")
                .title("Alice")
                .build();

        template = Template.builder()
                .id("tpl-1").loreId("lore-1").name("Personnage")
                .fields(List.of(
                        TemplateField.text("Histoire"),
                        TemplateField.text("Apparence"),
                        TemplateField.image("Portrait")))
                .build();

        lore = Lore.builder().id("lore-1").name("Aetheria").description("monde aérien").build();
        folder = LoreNode.builder().id("node-1").name("PNJ").loreId("lore-1").build();
    }

    @Test
    void testExecute_HappyPath_OnlyTextFieldsSentToAi() {
        when(pageRepository.findById("p-1")).thenReturn(Optional.of(page));
        when(templateRepository.findById("tpl-1")).thenReturn(Optional.of(template));
        when(loreRepository.findById("lore-1")).thenReturn(Optional.of(lore));
        when(loreNodeRepository.findById("node-1")).thenReturn(Optional.of(folder));

        Map<String, String> generated = Map.of(
                "Histoire", "Alice est une...",
                "Apparence", "Cheveux roux");
        when(aiProvider.generatePage(any())).thenReturn(new GenerationResult(generated));

        Map<String, String> result = useCase.execute("p-1");

        assertEquals(generated, result);

        ArgumentCaptor<GenerationContext> captor = ArgumentCaptor.forClass(GenerationContext.class);
        verify(aiProvider).generatePage(captor.capture());
        GenerationContext ctx = captor.getValue();

        assertEquals("Aetheria", ctx.getLoreName());
        assertEquals("monde aérien", ctx.getLoreDescription());
        assertEquals("PNJ", ctx.getFolderName());
        assertEquals("Personnage", ctx.getTemplateName());
        assertEquals("Alice", ctx.getPageTitle());
        // Seuls les champs TEXT doivent etre envoyes (pas "Portrait" IMAGE).
        assertEquals(List.of("Histoire", "Apparence"), ctx.getTemplateFields());
    }

    @Test
    void testExecute_PageNotFound() {
        when(pageRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> useCase.execute("missing"));
        verifyNoInteractions(aiProvider);
    }

    @Test
    void testExecute_PageWithoutTemplateId() {
        Page orphan = Page.builder().id("p-1").loreId("lore-1").nodeId("node-1")
                .templateId(null).title("Orphan").build();
        when(pageRepository.findById("p-1")).thenReturn(Optional.of(orphan));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> useCase.execute("p-1"));
        assertTrue(ex.getMessage().contains("template"));
    }

    @Test
    void testExecute_TemplateNotFound() {
        when(pageRepository.findById("p-1")).thenReturn(Optional.of(page));
        when(templateRepository.findById("tpl-1")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> useCase.execute("p-1"));
    }

    @Test
    void testExecute_LoreNotFound() {
        when(pageRepository.findById("p-1")).thenReturn(Optional.of(page));
        when(templateRepository.findById("tpl-1")).thenReturn(Optional.of(template));
        when(loreRepository.findById("lore-1")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> useCase.execute("p-1"));
    }

    @Test
    void testExecute_FolderNotFound() {
        when(pageRepository.findById("p-1")).thenReturn(Optional.of(page));
        when(templateRepository.findById("tpl-1")).thenReturn(Optional.of(template));
        when(loreRepository.findById("lore-1")).thenReturn(Optional.of(lore));
        when(loreNodeRepository.findById("node-1")).thenReturn(Optional.empty());

        assertThrows(IllegalStateException.class, () -> useCase.execute("p-1"));
    }

    @Test
    void testExecute_TemplateWithoutTextFields() {
        Template imageOnly = Template.builder()
                .id("tpl-1").loreId("lore-1").name("Galerie")
                .fields(List.of(new TemplateField("Portrait", FieldType.IMAGE)))
                .build();

        when(pageRepository.findById("p-1")).thenReturn(Optional.of(page));
        when(templateRepository.findById("tpl-1")).thenReturn(Optional.of(imageOnly));
        when(loreRepository.findById("lore-1")).thenReturn(Optional.of(lore));
        when(loreNodeRepository.findById("node-1")).thenReturn(Optional.of(folder));

        IllegalStateException ex = assertThrows(IllegalStateException.class,
                () -> useCase.execute("p-1"));
        assertTrue(ex.getMessage().contains("Galerie"));
        verifyNoInteractions(aiProvider);
    }
}
