package com.loremind.application.conversationcontext;

import com.loremind.domain.conversationcontext.Conversation;
import com.loremind.domain.conversationcontext.ConversationMessage;
import com.loremind.domain.conversationcontext.ports.ConversationRepository;
import com.loremind.domain.conversationcontext.ports.ConversationTitleGenerator;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

/**
 * Tests unitaires de ConversationService.
 * Focus sur :
 *  - la validation XOR de l'ancrage (loreId XOR campaignId),
 *  - la coherence entityType/entityId (tous deux null ou tous deux non-null),
 *  - le fallback du titre a la creation,
 *  - la validation des roles et contenus de message,
 *  - l'auto-generation de titre (cas succes + court-circuit si pas de messages).
 */
@ExtendWith(MockitoExtension.class)
class ConversationServiceTest {

    @Mock
    private ConversationRepository repository;

    @Mock
    private ConversationTitleGenerator titleGenerator;

    @InjectMocks
    private ConversationService service;

    // ---------- create : validation XOR de l'ancrage -----------------------

    @Test
    void create_rejectsBothAnchorsNull() {
        ConversationService.CreateData data = new ConversationService.CreateData(
                "t", null, null, null, null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(data));
        assertEquals("Exactement un parent attendu : loreId XOR campaignId", ex.getMessage());
        verifyNoInteractions(repository);
    }

    @Test
    void create_rejectsBothAnchorsPresent() {
        ConversationService.CreateData data = new ConversationService.CreateData(
                "t", "lore-1", "camp-1", null, null);
        assertThrows(IllegalArgumentException.class, () -> service.create(data));
        verifyNoInteractions(repository);
    }

    @Test
    void create_rejectsBlankLoreIdAsAbsent() {
        // Blank (espaces) = absent : c'est la regle du service.
        ConversationService.CreateData data = new ConversationService.CreateData(
                "t", "   ", "   ", null, null);
        assertThrows(IllegalArgumentException.class, () -> service.create(data));
    }

    @Test
    void create_rejectsEntityTypeWithoutEntityId() {
        ConversationService.CreateData data = new ConversationService.CreateData(
                "t", "lore-1", null, "page", null);
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class, () -> service.create(data));
        assertEquals("entityType et entityId doivent etre tous deux null ou tous deux non-null", ex.getMessage());
    }

    @Test
    void create_rejectsEntityIdWithoutEntityType() {
        ConversationService.CreateData data = new ConversationService.CreateData(
                "t", "lore-1", null, null, "page-42");
        assertThrows(IllegalArgumentException.class, () -> service.create(data));
    }

    // ---------- create : cas nominaux --------------------------------------

    @Test
    void create_withLoreAnchor_persistsBuiltConversation() {
        ConversationService.CreateData data = new ConversationService.CreateData(
                "Discussion Thorin", "lore-1", null, "page", "page-42");
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        Conversation result = service.create(data);

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(repository).save(captor.capture());
        Conversation saved = captor.getValue();
        assertEquals("Discussion Thorin", saved.getTitle());
        assertEquals("lore-1", saved.getLoreId());
        assertEquals("page", saved.getEntityType());
        assertEquals("page-42", saved.getEntityId());
        assertEquals(saved, result);
    }

    @Test
    void create_withCampaignAnchor_andNoEntityFocus_persistsRootLevel() {
        ConversationService.CreateData data = new ConversationService.CreateData(
                null, null, "camp-1", null, null);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(data);

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(repository).save(captor.capture());
        assertEquals("Nouvelle conversation", captor.getValue().getTitle(),
                "Titre absent -> fallback par defaut");
        assertEquals("camp-1", captor.getValue().getCampaignId());
    }

    @Test
    void create_trimsProvidedTitle() {
        ConversationService.CreateData data = new ConversationService.CreateData(
                "   Mon titre  ", "lore-1", null, null, null);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(data);

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(repository).save(captor.capture());
        assertEquals("Mon titre", captor.getValue().getTitle());
    }

    @Test
    void create_blankTitle_fallsBackToDefault() {
        ConversationService.CreateData data = new ConversationService.CreateData(
                "   ", "lore-1", null, null, null);
        when(repository.save(any())).thenAnswer(inv -> inv.getArgument(0));

        service.create(data);

        ArgumentCaptor<Conversation> captor = ArgumentCaptor.forClass(Conversation.class);
        verify(repository).save(captor.capture());
        assertEquals("Nouvelle conversation", captor.getValue().getTitle());
    }

    // ---------- getById / listByContext / delete --------------------------

    @Test
    void getById_delegatesToRepository() {
        Conversation conv = Conversation.builder().id("c-1").build();
        when(repository.findById("c-1")).thenReturn(Optional.of(conv));

        assertEquals(Optional.of(conv), service.getById("c-1"));
    }

    @Test
    void listByContext_validatesAnchorBeforeQuerying() {
        assertThrows(IllegalArgumentException.class,
                () -> service.listByContext(null, null, null, null));
        verifyNoInteractions(repository);
    }

    @Test
    void listByContext_delegates_whenAnchorValid() {
        Conversation c = Conversation.builder().id("c-1").build();
        when(repository.findByContext("lore-1", null, null, null)).thenReturn(List.of(c));

        List<Conversation> result = service.listByContext("lore-1", null, null, null);

        assertEquals(1, result.size());
    }

    @Test
    void delete_delegatesToRepository() {
        service.delete("c-1");
        verify(repository).deleteById("c-1");
    }

    // ---------- rename -----------------------------------------------------

    @Test
    void rename_rejectsNullTitle() {
        assertThrows(IllegalArgumentException.class, () -> service.rename("c-1", null));
        verify(repository, never()).updateTitle(anyString(), anyString());
    }

    @Test
    void rename_rejectsBlankTitle() {
        assertThrows(IllegalArgumentException.class, () -> service.rename("c-1", "   "));
        verify(repository, never()).updateTitle(anyString(), anyString());
    }

    @Test
    void rename_rejectsUnknownConversation() {
        when(repository.findById("unknown")).thenReturn(Optional.empty());

        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.rename("unknown", "Nouveau titre"));
        assertEquals("Conversation introuvable : unknown", ex.getMessage());
        verify(repository, never()).updateTitle(anyString(), anyString());
    }

    @Test
    void rename_trimsTitleBeforePersist() {
        when(repository.findById("c-1")).thenReturn(Optional.of(Conversation.builder().id("c-1").build()));

        service.rename("c-1", "  Nouveau titre  ");

        verify(repository).updateTitle("c-1", "Nouveau titre");
    }

    // ---------- appendMessage ----------------------------------------------

    @Test
    void appendMessage_rejectsInvalidRole() {
        IllegalArgumentException ex = assertThrows(IllegalArgumentException.class,
                () -> service.appendMessage("c-1", "admin", "hello"));
        assertEquals("Role invalide : admin", ex.getMessage());
        verifyNoInteractions(repository);
    }

    @Test
    void appendMessage_rejectsNullRole() {
        assertThrows(IllegalArgumentException.class,
                () -> service.appendMessage("c-1", null, "hello"));
    }

    @Test
    void appendMessage_rejectsNullContent() {
        assertThrows(IllegalArgumentException.class,
                () -> service.appendMessage("c-1", "user", null));
    }

    @Test
    void appendMessage_rejectsEmptyContent() {
        assertThrows(IllegalArgumentException.class,
                () -> service.appendMessage("c-1", "user", ""));
    }

    @Test
    void appendMessage_acceptsAllThreeCanonicalRoles() {
        ConversationMessage returned = ConversationMessage.builder().id("m").build();
        when(repository.appendMessage(eq("c-1"), any())).thenReturn(returned);

        for (String role : new String[]{"user", "assistant", "system"}) {
            service.appendMessage("c-1", role, "contenu");
        }

        ArgumentCaptor<ConversationMessage> captor = ArgumentCaptor.forClass(ConversationMessage.class);
        verify(repository, times(3)).appendMessage(eq("c-1"), captor.capture());
        assertEquals("user", captor.getAllValues().get(0).getRole());
        assertEquals("assistant", captor.getAllValues().get(1).getRole());
        assertEquals("system", captor.getAllValues().get(2).getRole());
    }

    @Test
    void appendMessage_passesContentVerbatim() {
        ConversationMessage returned = ConversationMessage.builder().id("m-1").build();
        when(repository.appendMessage(eq("c-1"), any())).thenReturn(returned);

        service.appendMessage("c-1", "user", "   hello avec espaces   ");

        ArgumentCaptor<ConversationMessage> captor = ArgumentCaptor.forClass(ConversationMessage.class);
        verify(repository).appendMessage(eq("c-1"), captor.capture());
        // Le service NE trim PAS le contenu — seul le titre est trim.
        assertEquals("   hello avec espaces   ", captor.getValue().getContent());
    }

    // ---------- autoGenerateTitle ------------------------------------------

    @Test
    void autoGenerateTitle_throws_whenConversationNotFound() {
        when(repository.findById("unknown")).thenReturn(Optional.empty());

        assertThrows(IllegalArgumentException.class, () -> service.autoGenerateTitle("unknown"));
        verifyNoInteractions(titleGenerator);
    }

    @Test
    void autoGenerateTitle_shortCircuits_whenNoMessages() {
        Conversation conv = Conversation.builder().id("c-1").title("Titre existant").messages(List.of()).build();
        when(repository.findById("c-1")).thenReturn(Optional.of(conv));

        String result = service.autoGenerateTitle("c-1");

        assertEquals("Titre existant", result);
        verifyNoInteractions(titleGenerator);
        verify(repository, never()).updateTitle(anyString(), anyString());
    }

    @Test
    void autoGenerateTitle_shortCircuits_whenMessagesIsNull() {
        Conversation conv = new Conversation();  // @NoArgsConstructor -> messages == null
        conv.setId("c-1");
        conv.setTitle("Titre");
        when(repository.findById("c-1")).thenReturn(Optional.of(conv));

        assertEquals("Titre", service.autoGenerateTitle("c-1"));
        verifyNoInteractions(titleGenerator);
    }

    @Test
    void autoGenerateTitle_generatesAndPersists_whenMessagesPresent() {
        List<ConversationMessage> seeds = List.of(
                ConversationMessage.builder().role("user").content("bonjour").build(),
                ConversationMessage.builder().role("assistant").content("salut").build());
        Conversation conv = Conversation.builder().id("c-1").title("Ancien").messages(seeds).build();

        when(repository.findById("c-1")).thenReturn(Optional.of(conv));
        when(titleGenerator.generate(seeds)).thenReturn("Premier echange poli");

        String result = service.autoGenerateTitle("c-1");

        assertEquals("Premier echange poli", result);
        verify(repository).updateTitle("c-1", "Premier echange poli");
    }
}
