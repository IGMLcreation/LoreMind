package com.loremind.application.generationcontext;

import com.loremind.domain.campaigncontext.Campaign;
import com.loremind.domain.campaigncontext.ports.CampaignRepository;
import com.loremind.domain.generationcontext.CampaignStructuralContext;
import com.loremind.domain.generationcontext.ChatMessage;
import com.loremind.domain.generationcontext.ChatRequest;
import com.loremind.domain.generationcontext.ChatUsage;
import com.loremind.domain.generationcontext.LoreStructuralContext;
import com.loremind.domain.generationcontext.NarrativeEntityContext;
import com.loremind.domain.generationcontext.ports.AiChatProvider;
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
 * Test unitaire pour StreamChatForCampaignUseCase.
 * Vérifie l'orchestration : chargement Campaign, chargement optionnel du
 * Lore lié (avec tolérance d'un Lore supprimé), chargement optionnel de
 * l'entité narrative focus, et délégation au port AiChatProvider avec la
 * ChatRequest correcte.
 */
@ExtendWith(MockitoExtension.class)
public class StreamChatForCampaignUseCaseTest {

    @Mock private CampaignRepository campaignRepository;
    @Mock private CampaignStructuralContextBuilder campaignContextBuilder;
    @Mock private LoreStructuralContextBuilder loreContextBuilder;
    @Mock private NarrativeEntityContextBuilder narrativeEntityContextBuilder;
    @Mock private AiChatProvider aiChatProvider;

    @InjectMocks private StreamChatForCampaignUseCase useCase;

    private CampaignStructuralContext campaignCtx;
    private List<ChatMessage> messages;
    private Consumer<ChatUsage> onUsage;
    private Consumer<String> onToken;
    private Runnable onComplete;
    private Consumer<Throwable> onError;

    @SuppressWarnings("unchecked")
    @BeforeEach
    void setUp() {
        campaignCtx = new CampaignStructuralContext("X", "d", List.of(), List.of(), List.of());
        messages = List.of();
        onUsage = mock(Consumer.class);
        onToken = mock(Consumer.class);
        onComplete = mock(Runnable.class);
        onError = mock(Consumer.class);
    }

    @Test
    void testExecute_CampaignNotFound_Throws() {
        when(campaignRepository.findById("missing")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class,
                () -> useCase.execute("missing", null, null, messages, onUsage, onToken, onComplete, onError));
        verifyNoInteractions(aiChatProvider);
    }

    @Test
    void testExecute_StandaloneCampaign_NoLoreNoEntity() {
        Campaign standalone = Campaign.builder().id("c-1").name("C").loreId(null).build();
        when(campaignRepository.findById("c-1")).thenReturn(Optional.of(standalone));
        when(campaignContextBuilder.build("c-1")).thenReturn(campaignCtx);

        useCase.execute("c-1", null, null, messages, onUsage, onToken, onComplete, onError);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiChatProvider).streamChat(captor.capture(), eq(onUsage), eq(onToken), eq(onComplete), eq(onError));
        ChatRequest req = captor.getValue();
        assertSame(campaignCtx, req.campaignContext());
        assertNull(req.loreContext());
        assertNull(req.narrativeEntity());
        assertNull(req.pageContext());
        verifyNoInteractions(loreContextBuilder);
        verifyNoInteractions(narrativeEntityContextBuilder);
    }

    @Test
    void testExecute_LinkedCampaign_LoadsLoreContext() {
        Campaign linked = Campaign.builder().id("c-1").name("C").loreId("lore-1").build();
        LoreStructuralContext loreCtx = new LoreStructuralContext(
                "L", "d", Collections.emptyMap(), List.of());

        when(campaignRepository.findById("c-1")).thenReturn(Optional.of(linked));
        when(campaignContextBuilder.build("c-1")).thenReturn(campaignCtx);
        when(loreContextBuilder.buildOptional("lore-1")).thenReturn(Optional.of(loreCtx));

        useCase.execute("c-1", null, null, messages, onUsage, onToken, onComplete, onError);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiChatProvider).streamChat(captor.capture(), any(), any(), any(), any());
        assertSame(loreCtx, captor.getValue().loreContext());
    }

    @Test
    void testExecute_LinkedCampaignButLoreDeleted_ContinuesWithNullLore() {
        Campaign linked = Campaign.builder().id("c-1").name("C").loreId("lore-ghost").build();

        when(campaignRepository.findById("c-1")).thenReturn(Optional.of(linked));
        when(campaignContextBuilder.build("c-1")).thenReturn(campaignCtx);
        when(loreContextBuilder.buildOptional("lore-ghost")).thenReturn(Optional.empty());

        useCase.execute("c-1", null, null, messages, onUsage, onToken, onComplete, onError);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiChatProvider).streamChat(captor.capture(), any(), any(), any(), any());
        assertNull(captor.getValue().loreContext());
        // La requete doit tout de meme partir (pas d'exception).
    }

    @Test
    void testExecute_WithEntityFocus_BuildsNarrativeEntity() {
        Campaign standalone = Campaign.builder().id("c-1").name("C").loreId(null).build();
        NarrativeEntityContext entity = new NarrativeEntityContext("scene", "L'auberge", Map.of());

        when(campaignRepository.findById("c-1")).thenReturn(Optional.of(standalone));
        when(campaignContextBuilder.build("c-1")).thenReturn(campaignCtx);
        when(narrativeEntityContextBuilder.build("scene", "s-1")).thenReturn(entity);

        useCase.execute("c-1", "scene", "s-1", messages, onUsage, onToken, onComplete, onError);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiChatProvider).streamChat(captor.capture(), any(), any(), any(), any());
        assertSame(entity, captor.getValue().narrativeEntity());
    }

    @Test
    void testExecute_BlankEntityTypeOrId_NoNarrativeEntityLoaded() {
        Campaign standalone = Campaign.builder().id("c-1").name("C").loreId(null).build();
        when(campaignRepository.findById("c-1")).thenReturn(Optional.of(standalone));
        when(campaignContextBuilder.build("c-1")).thenReturn(campaignCtx);

        useCase.execute("c-1", "scene", "   ", messages, onUsage, onToken, onComplete, onError);

        ArgumentCaptor<ChatRequest> captor = ArgumentCaptor.forClass(ChatRequest.class);
        verify(aiChatProvider).streamChat(captor.capture(), any(), any(), any(), any());
        assertNull(captor.getValue().narrativeEntity());
        verifyNoInteractions(narrativeEntityContextBuilder);
    }
}
