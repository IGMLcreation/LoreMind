package com.loremind.application.conversationcontext;

import com.loremind.domain.conversationcontext.Conversation;
import com.loremind.domain.conversationcontext.ConversationMessage;
import com.loremind.domain.conversationcontext.ports.ConversationRepository;
import com.loremind.domain.conversationcontext.ports.ConversationTitleGenerator;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

/**
 * Service d'application du contexte Conversation.
 *
 * Regroupe les cas d'usage CRUD + append message + rename. Un seul
 * service suffit — le contexte est simple et les operations fortement
 * liees (meme aggregat).
 *
 * Regles metier :
 *  - exactement un ancrage parent (loreId XOR campaignId) ;
 *  - entityType et entityId vont ensemble (tous deux null = niveau racine,
 *    tous deux non-null = niveau entite precise).
 */
@Service
public class ConversationService {

    private final ConversationRepository repository;
    private final ConversationTitleGenerator titleGenerator;

    public ConversationService(ConversationRepository repository,
                               ConversationTitleGenerator titleGenerator) {
        this.repository = repository;
        this.titleGenerator = titleGenerator;
    }

    /** Donnees de creation d'une conversation. Titre optionnel — sera auto-genere si absent. */
    public record CreateData(
            String title,
            String loreId,
            String campaignId,
            String entityType,
            String entityId) {}

    public Conversation create(CreateData data) {
        validateAnchor(data.loreId(), data.campaignId(), data.entityType(), data.entityId());

        String title = (data.title() == null || data.title().isBlank())
                ? "Nouvelle conversation"
                : data.title().trim();

        Conversation conv = Conversation.builder()
                .title(title)
                .loreId(data.loreId())
                .campaignId(data.campaignId())
                .entityType(data.entityType())
                .entityId(data.entityId())
                .build();
        return repository.save(conv);
    }

    public Optional<Conversation> getById(String id) {
        return repository.findById(id);
    }

    public List<Conversation> listByContext(String loreId, String campaignId, String entityType, String entityId) {
        validateAnchor(loreId, campaignId, entityType, entityId);
        return repository.findByContext(loreId, campaignId, entityType, entityId);
    }

    public void rename(String id, String title) {
        if (title == null || title.isBlank()) {
            throw new IllegalArgumentException("Le titre ne peut pas etre vide");
        }
        if (repository.findById(id).isEmpty()) {
            throw new IllegalArgumentException("Conversation introuvable : " + id);
        }
        repository.updateTitle(id, title.trim());
    }

    public void delete(String id) {
        repository.deleteById(id);
    }

    /**
     * Auto-genere un titre a partir des premiers messages et le persiste.
     * Appele typiquement apres le 1er couple user/assistant pour remplacer
     * le titre provisoire. Echec silencieux (fallback dans l'adaptateur) —
     * on n'empeche pas la conversation de fonctionner si le Brain est down.
     */
    public String autoGenerateTitle(String conversationId) {
        Conversation conv = repository.findById(conversationId)
                .orElseThrow(() -> new IllegalArgumentException("Conversation introuvable : " + conversationId));
        List<ConversationMessage> seeds = conv.getMessages();
        if (seeds == null || seeds.isEmpty()) {
            return conv.getTitle();
        }
        String title = titleGenerator.generate(seeds);
        repository.updateTitle(conversationId, title);
        return title;
    }

    /**
     * Ajoute un message (user ou assistant) a une conversation existante.
     * L'horodatage et l'id sont assignes par la couche persistance.
     */
    public ConversationMessage appendMessage(String conversationId, String role, String content) {
        if (role == null || (!role.equals("user") && !role.equals("assistant") && !role.equals("system"))) {
            throw new IllegalArgumentException("Role invalide : " + role);
        }
        if (content == null || content.isEmpty()) {
            throw new IllegalArgumentException("Contenu vide interdit");
        }
        ConversationMessage msg = ConversationMessage.builder()
                .role(role)
                .content(content)
                .build();
        return repository.appendMessage(conversationId, msg);
    }

    // ---------- Validation ----------

    private void validateAnchor(String loreId, String campaignId, String entityType, String entityId) {
        boolean hasLore = loreId != null && !loreId.isBlank();
        boolean hasCamp = campaignId != null && !campaignId.isBlank();
        if (hasLore == hasCamp) {
            throw new IllegalArgumentException("Exactement un parent attendu : loreId XOR campaignId");
        }
        boolean hasType = entityType != null && !entityType.isBlank();
        boolean hasEntId = entityId != null && !entityId.isBlank();
        if (hasType != hasEntId) {
            throw new IllegalArgumentException("entityType et entityId doivent etre tous deux null ou tous deux non-null");
        }
    }
}
