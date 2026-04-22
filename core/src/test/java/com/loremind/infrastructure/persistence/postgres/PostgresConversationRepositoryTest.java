package com.loremind.infrastructure.persistence.postgres;

import com.loremind.domain.conversationcontext.Conversation;
import com.loremind.domain.conversationcontext.ConversationMessage;
import com.loremind.domain.conversationcontext.ports.ConversationRepository;
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
 * Tests d'integration pour PostgresConversationRepository.
 * Focus particulier sur le filtrage contextuel (findByContext) avec ses 4
 * variantes : Lore racine, Lore + entite, Campaign racine, Campaign + entite.
 */
@SpringBootTest
@Transactional
class PostgresConversationRepositoryTest {

    @Autowired private ConversationRepository repository;

    @Test
    void save_conversationWithLoreAnchor_roundTrips() {
        Conversation c = Conversation.builder()
                .title("Discussion Thorin")
                .loreId("lore-1")
                .entityType("page").entityId("page-42")
                .build();

        Conversation saved = repository.save(c);
        assertNotNull(saved.getId());

        Conversation r = repository.findById(saved.getId()).orElseThrow();
        assertEquals("Discussion Thorin", r.getTitle());
        assertEquals("lore-1", r.getLoreId());
        assertEquals("page", r.getEntityType());
        assertEquals("page-42", r.getEntityId());
    }

    @Test
    void save_conversationWithCampaignAnchor_roundTrips() {
        Conversation c = Conversation.builder()
                .title("Sur la scene")
                .campaignId("camp-1")
                .entityType("scene").entityId("sc-7")
                .build();
        Conversation saved = repository.save(c);

        Conversation r = repository.findById(saved.getId()).orElseThrow();
        assertEquals("camp-1", r.getCampaignId());
        assertEquals("scene", r.getEntityType());
    }

    @Test
    void findByContext_lorerootExcludesLoreWithEntityFocus() {
        // Lore racine : loreId=X, entityType=null, entityId=null
        repository.save(Conversation.builder().title("root-1").loreId("lore-1").build());
        repository.save(Conversation.builder().title("root-2").loreId("lore-1").build());
        // Meme lore MAIS focus sur une page — ne doit PAS apparaitre dans la liste racine.
        repository.save(Conversation.builder().title("page-scoped")
                .loreId("lore-1").entityType("page").entityId("p-1").build());

        List<Conversation> roots = repository.findByContext("lore-1", null, null, null);
        assertEquals(2, roots.size());
        assertTrue(roots.stream().allMatch(c -> c.getEntityType() == null));
    }

    @Test
    void findByContext_loreWithEntityFocus_filtersByType_andId() {
        repository.save(Conversation.builder().title("p1")
                .loreId("lore-1").entityType("page").entityId("p-1").build());
        repository.save(Conversation.builder().title("p1-bis")
                .loreId("lore-1").entityType("page").entityId("p-1").build());
        repository.save(Conversation.builder().title("p2")
                .loreId("lore-1").entityType("page").entityId("p-2").build());

        List<Conversation> hits = repository.findByContext("lore-1", null, "page", "p-1");
        assertEquals(2, hits.size());
    }

    @Test
    void findByContext_campaignRoot_excludesCampaignWithEntityFocus() {
        repository.save(Conversation.builder().title("c-root").campaignId("camp-1").build());
        repository.save(Conversation.builder().title("c-scene")
                .campaignId("camp-1").entityType("scene").entityId("sc-1").build());

        List<Conversation> roots = repository.findByContext(null, "camp-1", null, null);
        assertEquals(1, roots.size());
        assertEquals("c-root", roots.get(0).getTitle());
    }

    @Test
    void appendMessage_persistsNewMessage_andFindByIdExposesIt() {
        Conversation saved = repository.save(Conversation.builder()
                .title("t").loreId("lore-1").build());

        repository.appendMessage(saved.getId(),
                ConversationMessage.builder().role("user").content("bonjour").build());
        repository.appendMessage(saved.getId(),
                ConversationMessage.builder().role("assistant").content("salut").build());

        Conversation reloaded = repository.findById(saved.getId()).orElseThrow();
        assertEquals(2, reloaded.getMessages().size());
        assertEquals("user", reloaded.getMessages().get(0).getRole());
        assertEquals("assistant", reloaded.getMessages().get(1).getRole());
        assertEquals("bonjour", reloaded.getMessages().get(0).getContent());
    }

    @Test
    void updateTitle_changesTitle_withoutTouchingMessages() {
        Conversation saved = repository.save(Conversation.builder()
                .title("ancien").loreId("lore-1").build());
        repository.appendMessage(saved.getId(),
                ConversationMessage.builder().role("user").content("test").build());

        repository.updateTitle(saved.getId(), "nouveau");

        Conversation r = repository.findById(saved.getId()).orElseThrow();
        assertEquals("nouveau", r.getTitle());
        assertEquals(1, r.getMessages().size(), "Les messages ne doivent pas etre affectes");
    }

    @Test
    void deleteById_removesConversation() {
        Conversation saved = repository.save(Conversation.builder()
                .title("t").loreId("lore-1").build());
        assertTrue(repository.findById(saved.getId()).isPresent());

        repository.deleteById(saved.getId());

        assertFalse(repository.findById(saved.getId()).isPresent());
    }
}
