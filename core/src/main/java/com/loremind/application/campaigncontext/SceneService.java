package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.generation.FieldProposal;
import com.loremind.domain.campaigncontext.structure.Scene;
import com.loremind.domain.campaigncontext.generation.SceneDraft;
import com.loremind.domain.shared.ReorderSupport;
import com.loremind.domain.campaigncontext.structure.SceneBranch;
import com.loremind.domain.campaigncontext.ports.SceneRepository;
import org.springframework.beans.BeanUtils;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.stream.Collectors;

/**
 * Service d'application pour le contexte Scene.
 * Orchestre la logique métier en utilisant le Port SceneRepository.
 * Fait partie de la couche Application de l'Architecture Hexagonale.
 */
@Service
public class SceneService {

    private final SceneRepository sceneRepository;

    public SceneService(SceneRepository sceneRepository) {
        this.sceneRepository = sceneRepository;
    }

    public Scene createScene(String name, String description, String chapterId, int order) {
        return createScene(name, description, chapterId, order, null);
    }

    public Scene createScene(String name, String description, String chapterId, int order, String icon) {
        Scene scene = Scene.builder()
                .name(name)
                .description(description)
                .chapterId(chapterId)
                .order(order)
                .icon(icon)
                .build();
        return sceneRepository.save(scene);
    }

    /**
     * Crée une scène à partir d'un objet Scene complet (tous les champs : narration
     * joueurs, notes MJ, pièces…). Utilisé par l'import de campagne. L'ID est forcé
     * à null pour laisser le repo en générer un nouveau.
     */
    public Scene createScene(Scene input) {
        input.setId(null);
        if (input.getRooms() == null) {
            input.setRooms(new ArrayList<>());
        }
        return sceneRepository.save(input);
    }

    public Optional<Scene> getSceneById(String id) {
        return sceneRepository.findById(id);
    }

    public List<Scene> getAllScenes() {
        return sceneRepository.findAll();
    }

    public List<Scene> getScenesByChapterId(String chapterId) {
        return sceneRepository.findByChapterId(chapterId);
    }

    /**
     * Met à jour une Scene avec tous ses champs narratifs (Parameter Object pattern).
     */
    public Scene updateScene(String id, Scene updated) {
        Optional<Scene> existingScene = sceneRepository.findById(id);
        if (existingScene.isEmpty()) {
            throw new IllegalArgumentException("Scene non trouvée avec l'ID: " + id);
        }

        Scene scene = existingScene.get();
        BeanUtils.copyProperties(updated, scene, "id");

        // Validation métier : le graphe narratif doit rester cohérent.
        validateBranches(scene);

        return sceneRepository.save(scene);
    }

    /**
     * Patch CIBLÉ champ-par-champ d'une scène (Pilier A — co-création). Applique
     * UNIQUEMENT les {@link FieldProposal} reçus (valeurs acceptées par l'utilisateur) sur
     * les champs correspondants ; tous les autres champs restent INTACTS.
     *
     * <p>Contraste volontaire avec {@link #updateScene} : ce dernier fait un
     * {@code BeanUtils.copyProperties} qui écrase MÊME avec des null — inadapté ici où l'on
     * ne veut toucher que les champs proposés. Les branches ne sont pas modifiées (pas de
     * revalidation du graphe nécessaire).</p>
     */
    @Transactional
    public Scene patchScene(String id, List<FieldProposal> fields) {
        Scene scene = sceneRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Scene non trouvée avec l'ID: " + id));
        if (fields != null) {
            for (FieldProposal f : fields) {
                if (f == null || f.key() == null) continue;
                applyField(scene, f.key(), f.proposedValue());
            }
        }
        return sceneRepository.save(scene);
    }

    /**
     * Applique une valeur sur le champ nommé. Whitelist STRICTE alignée sur
     * {@code NarrativeEntityContextBuilder.fromScene()} : toute clé inconnue est ignorée
     * (jamais d'écrasement hors de la liste connue).
     */
    private void applyField(Scene scene, String key, String value) {
        switch (key) {
            case "description" -> scene.setDescription(value);
            case "location" -> scene.setLocation(value);
            case "timing" -> scene.setTiming(value);
            case "atmosphere" -> scene.setAtmosphere(value);
            case "playerNarration" -> scene.setPlayerNarration(value);
            case "choicesConsequences" -> scene.setChoicesConsequences(value);
            case "combatDifficulty" -> scene.setCombatDifficulty(value);
            case "enemies" -> scene.setEnemies(value);
            case "gmSecretNotes" -> scene.setGmSecretNotes(value);
            default -> { /* clé inconnue → ignorée (garde-fou anti-écrasement) */ }
        }
    }

    /**
     * Crée en bloc des scènes à partir d'ébauches IA acceptées (Pilier A — capacité
     * « create »). Les scènes sont AJOUTÉES à la fin du chapitre (ordre = suite des scènes
     * existantes). Les ébauches sans titre sont ignorées. Transactionnel.
     */
    @Transactional
    public List<Scene> createDraftScenes(String chapterId, List<SceneDraft> drafts) {
        if (drafts == null || drafts.isEmpty()) return List.of();
        int order = sceneRepository.findByChapterId(chapterId).stream()
                .mapToInt(Scene::getOrder).max().orElse(-1) + 1;
        List<Scene> created = new ArrayList<>();
        for (SceneDraft d : drafts) {
            if (d == null || d.name() == null || d.name().isBlank()) continue;
            Scene scene = Scene.builder()
                    .name(d.name().trim())
                    .description(d.description())
                    .playerNarration(d.playerNarration())
                    .chapterId(chapterId)
                    .order(order++)
                    .build();
            created.add(createScene(scene));   // createScene(Scene) force id=null + rooms
        }
        return created;
    }

    /**
     * Supprime la scène ET nettoie les branches des scènes sœurs qui pointaient vers elle
     * (sinon elles deviennent des références mortes : invisibles dans le graphe — qui filtre
     * les cibles inexistantes — mais signalées « branche cassée » par le guidage, ce qui est
     * incompréhensible pour l'utilisateur). Les branches étant intra-chapitre, le nettoyage
     * se limite aux sœurs du même chapitre. Transactionnel : atomique.
     */
    @Transactional
    public void deleteScene(String id) {
        sceneRepository.findById(id).ifPresent(scene -> {
            for (Scene sibling : sceneRepository.findByChapterId(scene.getChapterId())) {
                List<SceneBranch> branches = sibling.getBranches();
                if (id.equals(sibling.getId()) || branches == null || branches.isEmpty()) continue;
                List<SceneBranch> kept = branches.stream()
                        .filter(b -> !id.equals(b.targetSceneId()))
                        .toList();
                if (kept.size() != branches.size()) {
                    sibling.setBranches(kept);
                    sceneRepository.save(sibling);
                }
            }
        });
        sceneRepository.deleteById(id);
    }

    public boolean sceneExists(String id) {
        return sceneRepository.existsById(id);
    }

    /**
     * Réordonne les scènes d'un chapitre : {@code order} = position. Si {@code chapterId}
     * diffère (scène déplacée vers un autre chapitre), on réaffecte le chapitre et on
     * vide ses branches (elles ne valent que dans le chapitre d'origine). Transactionnel.
     */
    @Transactional
    public void reorderScenes(String chapterId, List<String> orderedIds) {
        ReorderSupport.reorder(orderedIds,
                sceneRepository::findById,
                (scene, i) -> {
                    if (chapterId != null && !chapterId.isBlank() && !chapterId.equals(scene.getChapterId())) {
                        scene.setChapterId(chapterId);
                        scene.setBranches(new ArrayList<>());
                    }
                    scene.setOrder(i);
                },
                sceneRepository::save);
    }

    /**
     * Vérifie les invariants du graphe narratif :
     * 1. Pas d'auto-référence (scène qui pointe sur elle-même).
     * 2. Toutes les branches pointent vers des scènes du MÊME chapitre.
     * 3. Pas de targetSceneId null/vide.
     * <p>
     * Note : on ne vérifie PAS l'existence réelle de chaque scène cible
     * individuellement (ça serait un N+1). On charge une seule fois les
     * IDs du chapitre et on compare.
     */
    private void validateBranches(Scene scene) {
        List<SceneBranch> branches = scene.getBranches();
        if (branches == null || branches.isEmpty()) return;

        // IDs des scènes du chapitre courant (référentiel de validation)
        Set<String> chapterSceneIds = sceneRepository.findByChapterId(scene.getChapterId()).stream()
                .map(Scene::getId)
                .collect(Collectors.toSet());

        for (SceneBranch b : branches) {
            String target = b.targetSceneId();
            if (target == null || target.isBlank()) {
                throw new IllegalArgumentException("Une branche doit avoir une scène de destination");
            }
            if (target.equals(scene.getId())) {
                throw new IllegalArgumentException("Une scène ne peut pas se brancher sur elle-même");
            }
            if (!chapterSceneIds.contains(target)) {
                throw new IllegalArgumentException(
                        "La branche pointe vers la scène " + target + " qui n'appartient pas au même chapitre");
            }
        }
    }
}
