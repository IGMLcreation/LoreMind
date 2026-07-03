package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Arc;
import com.loremind.domain.campaigncontext.ArcType;
import com.loremind.domain.campaigncontext.Chapter;
import com.loremind.domain.campaigncontext.NodeType;
import com.loremind.domain.campaigncontext.Quest;
import com.loremind.domain.campaigncontext.QuestNodeRef;
import com.loremind.domain.campaigncontext.ports.ArcRepository;
import com.loremind.domain.campaigncontext.ports.ChapterRepository;
import com.loremind.domain.campaigncontext.ports.QuestRepository;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import com.loremind.domain.playcontext.ports.QuestProgressionRepository;
import com.loremind.domain.shared.ReorderSupport;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Optional;

/**
 * Service d'application pour le contexte Quest (Niveau 1).
 * Orchestre la logique métier via le Port {@code QuestRepository}.
 */
@Service
public class QuestService {

    /** Nom de l'arc technique hébergeant les conteneurs des quêtes libres (invisible). */
    static final String SYSTEM_ARC_NAME = "Quêtes libres";

    private final QuestRepository questRepository;
    private final QuestProgressionRepository progressionRepository;
    private final ChapterRepository chapterRepository;
    private final SceneRepository sceneRepository;
    private final ArcRepository arcRepository;

    public QuestService(QuestRepository questRepository,
                        QuestProgressionRepository progressionRepository,
                        ChapterRepository chapterRepository,
                        SceneRepository sceneRepository,
                        ArcRepository arcRepository) {
        this.questRepository = questRepository;
        this.progressionRepository = progressionRepository;
        this.chapterRepository = chapterRepository;
        this.sceneRepository = sceneRepository;
        this.arcRepository = arcRepository;
    }

    /**
     * Création à partir d'une Quest complète. L'id est forcé à null (généré par la DB).
     *
     * <p>TOUTE quête créée sans nœud reçoit son CONTENEUR de scènes (chapitre jumeau,
     * même nom, masqué dans l'arbre par la fusion quête/jumeau) : une quête est un espace
     * jouable où le MJ crée ses scènes à la volée — qu'elle vive dans un arc HUB (le
     * conteneur y est rangé) ou LIBRE (le conteneur va dans l'arc technique {@code SYSTEM}
     * de la campagne, invisible et non exporté). Lier des nœuds existants à la création
     * (quête « transversale ») court-circuite le provisioning.</p>
     */
    @Transactional
    public Quest createQuest(Quest input) {
        input.setId(null);
        if (nullSafeNodes(input.getNodes()).isEmpty()) {
            provisionContainer(input);
        }
        return questRepository.save(input);
    }

    /**
     * Provisionne le conteneur de scènes d'une quête sans nœud et le référence
     * (mutation de {@code quest.nodes} — la sauvegarde reste à la charge de l'appelant).
     */
    private void provisionContainer(Quest quest) {
        String containerArcId = quest.getArcId() != null && !quest.getArcId().isBlank()
                ? quest.getArcId()
                : systemArcIdFor(quest.getCampaignId());
        int order = chapterRepository.findByArcId(containerArcId).stream()
                .mapToInt(Chapter::getOrder).max().orElse(-1) + 1;
        Chapter container = chapterRepository.save(Chapter.builder()
                .name(quest.getName())
                .description("")            // le narratif vit sur la quête, pas sur le conteneur
                .arcId(containerArcId)
                .order(order)
                .build());
        quest.setNodes(new ArrayList<>(List.of(
                new QuestNodeRef(NodeType.CHAPTER, container.getId(), 0))));
    }

    /** Arc technique (SYSTEM) de la campagne — créé au premier besoin. */
    private String systemArcIdFor(String campaignId) {
        return arcRepository.findByCampaignId(campaignId).stream()
                .filter(a -> a.getType() == ArcType.SYSTEM)
                .map(Arc::getId)
                .findFirst()
                .orElseGet(() -> arcRepository.save(Arc.builder()
                        .name(SYSTEM_ARC_NAME)
                        .description("")
                        .campaignId(campaignId)
                        .type(ArcType.SYSTEM)
                        .order(9999)
                        .build()).getId());
    }

    /** Le chapitre est-il un CONTENEUR de cette quête (jumeau hub ou hébergé en arc SYSTEM) ? */
    private boolean isContainerOf(Quest quest, Chapter chapter) {
        if (Objects.equals(quest.getArcId(), chapter.getArcId())) return true;
        return chapter.getArcId() != null && arcRepository.findById(chapter.getArcId())
                .map(a -> a.getType() == ArcType.SYSTEM)
                .orElse(false);
    }

    public Optional<Quest> getQuestById(String id) {
        return questRepository.findById(id);
    }

    public List<Quest> getQuestsByCampaignId(String campaignId) {
        return questRepository.findByCampaignId(campaignId);
    }

    /** Quêtes rattachées à un arc HUB. */
    public List<Quest> getQuestsByArcId(String arcId) {
        return questRepository.findByArcId(arcId);
    }

    public List<Quest> getAllQuests() {
        return questRepository.findAll();
    }

    /** Met à jour une Quest (Parameter Object pattern, comme ChapterService). */
    @Transactional
    public Quest updateQuest(String id, Quest updated) {
        Optional<Quest> existing = questRepository.findById(id);
        if (existing.isEmpty()) {
            throw new IllegalArgumentException("Quest non trouvée avec l'ID: " + id);
        }
        Quest quest = existing.get();
        String oldName = quest.getName();
        BeanUtils.copyProperties(updated, quest, "id");
        Quest saved = questRepository.save(quest);
        // Le conteneur jumeau porte le même nom que la quête (fusion dans l'arbre) :
        // il suit le renommage, sinon le guidage citerait encore l'ancien nom.
        // Vaut pour les quêtes de hub COMME pour les quêtes libres (conteneur en arc SYSTEM).
        if (oldName != null && !oldName.equals(saved.getName())) {
            for (QuestNodeRef node : nullSafeNodes(saved.getNodes())) {
                if (node.nodeType() != NodeType.CHAPTER) continue;
                chapterRepository.findById(node.nodeId()).ifPresent(ch -> {
                    if (oldName.equals(ch.getName()) && isContainerOf(saved, ch)) {
                        ch.setName(saved.getName());
                        chapterRepository.save(ch);
                    }
                });
            }
        }
        // Auto-réparation : une quête historique restée sans nœud (créée avant le
        // provisioning systématique) reçoit son espace de scènes à la première sauvegarde.
        if (nullSafeNodes(saved.getNodes()).isEmpty()) {
            provisionContainer(saved);
            return questRepository.save(saved);
        }
        return saved;
    }

    /**
     * Supprime la quête et, en cascade, ses {@code QuestProgression} dans toutes les Parties.
     *
     * <p>TODO (Phase 5) : signaler/nettoyer les {@code Prerequisite.QuestCompleted} pendants
     * d'autres quêtes qui pointaient celle-ci. Échec sûr aujourd'hui : un prérequis vers une
     * quête supprimée n'est jamais satisfait → la quête dépendante reste LOCKED (pas de corruption).</p>
     */
    @Transactional
    public void deleteQuest(String id) {
        Quest quest = questRepository.findById(id).orElse(null);
        progressionRepository.deleteByQuestId(id);
        questRepository.deleteById(id);
        if (quest == null) return;
        // Nettoyage du CONTENEUR (jumeau hub ou hébergé en arc SYSTEM) : un chapitre VIDE
        // (aucune scène), plus référencé par aucune autre quête, ne doit pas réapparaître
        // comme « chapitre vide » fantôme. S'il contient des scènes, on le GARDE (aucune
        // perte de contenu). Les chapitres simplement LIÉS (quête transversale pointant du
        // contenu réel d'un autre arc) ne sont JAMAIS touchés — isContainerOf les exclut.
        List<Quest> remaining = questRepository.findByCampaignId(quest.getCampaignId());
        for (QuestNodeRef node : nullSafeNodes(quest.getNodes())) {
            if (node.nodeType() != NodeType.CHAPTER) continue;
            chapterRepository.findById(node.nodeId()).ifPresent(ch -> {
                boolean container = isContainerOf(quest, ch);
                boolean empty = sceneRepository.findByChapterId(ch.getId()).isEmpty();
                boolean referencedElsewhere = remaining.stream()
                        .anyMatch(q -> nullSafeNodes(q.getNodes()).stream()
                                .anyMatch(n -> n.nodeType() == NodeType.CHAPTER
                                        && ch.getId().equals(n.nodeId())));
                if (container && empty && !referencedElsewhere) {
                    chapterRepository.deleteById(ch.getId());
                }
            });
        }
    }

    private static List<QuestNodeRef> nullSafeNodes(List<QuestNodeRef> nodes) {
        return nodes != null ? nodes : List.of();
    }

    public boolean questExists(String id) {
        return questRepository.existsById(id);
    }

    /** Réordonne les quêtes d'une campagne : {@code order} = position. Transactionnel. */
    @Transactional
    public void reorderQuests(String campaignId, List<String> orderedIds) {
        ReorderSupport.reorder(orderedIds,
                id -> questRepository.findById(id).orElse(null),
                (quest, i) -> {
                    if (campaignId != null && !campaignId.isBlank()) quest.setCampaignId(campaignId);
                    quest.setOrder(i);
                },
                questRepository::save);
    }
}
