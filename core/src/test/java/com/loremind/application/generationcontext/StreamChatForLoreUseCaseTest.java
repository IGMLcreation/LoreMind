package com.loremind.application.generationcontext;

import com.loremind.domain.generationcontext.ChatMessage;
import com.loremind.domain.generationcontext.ChatRequest;
import com.loremind.domain.generationcontext.ChatUsage;
import com.loremind.domain.generationcontext.LoreStructuralContext;
import com.loremind.domain.generationcontext.ports.AiChatProvider;
import com.loremind.domain.lorecontext.FieldType;
import com.loremind.domain.lorecontext.Page;
import com.loremind.domain.lorecontext.Template;
import com.loremind.domain.lorecontext.TemplateField;
import com.loremind.domain.lorecontext.ports.PageRepository;
import com.loremind.domain.lorecontext.ports.TemplateRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Consumer;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

/**
 * Test unitaire pour StreamChatForLoreUseCase.
 * Vérifie l'orchestration : chargement du LoreStructuralContext obligatoire,
 * construction conditionnelle du PageContext (sans / avec page / page sans
 * template), et délégation au port AiChatProvider avec la bonne ChatRequest.
 */
@ExtendWith(MockitoExtension.class)
public class StreamChatForLoreUseCaseTest {

    @Mock private LoreStructuralContextBuilder loreContextBuilder;
    @Mock private PageRepository pageRepository;
    @Mock private TemplateRepository templateRepository;
    @Mock private AiChatProvider aiChatProvider;

    @InjectMocks private StreamChatForLoreUseCase useCase;

    private LoreStructuralContext loreCtx;
    private List<ChatMessage> messages;
    private Consumer<ChatUsage> onUsage;
    private Consumer<String> onToken;
    private Runnable onComplete;
    private Consumer<Throwable> onError;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        loreCtx = LoreStructuralContext.builder()
                .loreName("Aetheria").loreDescription("d")
                .folders(Collections.emptyMap())
                .build();
        messages = List.of();
        onUsage = mock(Consumer.class);
        onToken = mock(Consumer.class);
        onComplete = mock(Runnable.class);
        onError = mock(Consumer.class);
    }

    @Test
    void testExecute_NoPageId_SendsRequestWithoutPageContext() {
        when(loreContextBuilder.build("lore-1")).thenReturn(loreCtx);

        useCase.execute("lore-1", null, messages, onUsage, onToken, onComplete, onError);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiChatProvider).streamChat(captor.capture(), eq(onUsage), eq(onToken), eq(onComplete), eq(onError));
        ChatRequest req = captor.getValue();
        assertSame(loreCtx, req.getLoreContext());
        assertNull(req.getPageContext());
        assertNull(req.getCampaignContext());
    }

    @Test
    void testExecute_BlankPageId_TreatedAsNoPage() {
        when(loreContextBuilder.build("lore-1")).thenReturn(loreCtx);

        useCase.execute("lore-1", "   ", messages, onUsage, onToken, onComplete, onError);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiChatProvider).streamChat(captor.capture(), any(), any(), any(), any());
        assertNull(captor.getValue().getPageContext());
        verifyNoInteractions(pageRepository);
    }

    @Test
    void testExecute_WithPageAndTemplate_BuildsPageContext() {
        Template tpl = Template.builder()
                .id("tpl-1").name("Personnage")
                .fields(List.of(
                        TemplateField.text("Histoire"),
                        new TemplateField("Portrait", FieldType.IMAGE)))
                .build();
        Map<String, String> values = Map.of("Histoire", "...");
        Page page = Page.builder()
                .id("p-1").loreId("lore-1").nodeId("n-1")
                .templateId("tpl-1").title("Alice")
                .values(values)
                .build();

        when(loreContextBuilder.build("lore-1")).thenReturn(loreCtx);
        when(pageRepository.findById("p-1")).thenReturn(Optional.of(page));
        when(templateRepository.findById("tpl-1")).thenReturn(Optional.of(tpl));

        useCase.execute("lore-1", "p-1", messages, onUsage, onToken, onComplete, onError);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiChatProvider).streamChat(captor.capture(), any(), any(), any(), any());
        ChatRequest req = captor.getValue();
        assertNotNull(req.getPageContext());
        assertEquals("Alice", req.getPageContext().getTitle());
        assertEquals("Personnage", req.getPageContext().getTemplateName());
        // Seuls les champs TEXT exposes
        assertEquals(List.of("Histoire"), req.getPageContext().getTemplateFields());
        assertEquals(values, req.getPageContext().getValues());
    }

    @Test
    void testExecute_PageWithoutTemplate_FallbackContext() {
        Page page = Page.builder()
                .id("p-1").loreId("lore-1").nodeId("n-1")
                .templateId(null).title("Orphan").values(null)
                .build();
        when(loreContextBuilder.build("lore-1")).thenReturn(loreCtx);
        when(pageRepository.findById("p-1")).thenReturn(Optional.of(page));

        useCase.execute("lore-1", "p-1", messages, onUsage, onToken, onComplete, onError);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiChatProvider).streamChat(captor.capture(), any(), any(), any(), any());
        var pageCtx = captor.getValue().getPageContext();
        assertNotNull(pageCtx);
        assertEquals("Orphan", pageCtx.getTitle());
        assertEquals("?", pageCtx.getTemplateName());
        assertTrue(pageCtx.getTemplateFields().isEmpty());
        assertTrue(pageCtx.getValues().isEmpty());
        verifyNoInteractions(templateRepository);
    }

    @Test
    void testExecute_PageWithTemplateIdButTemplateMissing_FallbackToQuestionMark() {
        Page page = Page.builder()
                .id("p-1").loreId("lore-1").nodeId("n-1")
                .templateId("tpl-ghost").title("Alice").values(Map.of("k", "v"))
                .build();
        when(loreContextBuilder.build("lore-1")).thenReturn(loreCtx);
        when(pageRepository.findById("p-1")).thenReturn(Optional.of(page));
        when(templateRepository.findById("tpl-ghost")).thenReturn(Optional.empty());

        useCase.execute("lore-1", "p-1", messages, onUsage, onToken, onComplete, onError);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiChatProvider).streamChat(captor.capture(), any(), any(), any(), any());
        var pageCtx = captor.getValue().getPageContext();
        assertEquals("?", pageCtx.getTemplateName());
        assertTrue(pageCtx.getTemplateFields().isEmpty());
    }

    @Test
    void testExecute_PageNotFound_Throws() {
        when(loreContextBuilder.build("lore-1")).thenReturn(loreCtx);
        when(pageRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute("lore-1", "missing", messages, onUsage, onToken, onComplete, onError));
        verifyNoInteractions(aiChatProvider);
    }
}
