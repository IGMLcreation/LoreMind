package com.loremind.application.gamesystemcontext;

import com.loremind.domain.gamesystemcontext.GameSystem;
import com.loremind.domain.gamesystemcontext.RulesImportResult;
import com.loremind.domain.gamesystemcontext.ports.GameSystemRepository;
import com.loremind.domain.gamesystemcontext.ports.RulesPdfImporter;
import com.loremind.domain.shared.template.FieldType;
import com.loremind.domain.shared.template.TemplateField;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Service
public class GameSystemService {

    private final GameSystemRepository gameSystemRepository;
    private final RulesPdfImporter rulesPdfImporter;

    public GameSystemService(GameSystemRepository gameSystemRepository,
                             RulesPdfImporter rulesPdfImporter) {
        this.gameSystemRepository = gameSystemRepository;
        this.rulesPdfImporter = rulesPdfImporter;
    }

    /**
     * Importe un PDF de règles et renvoie une PROPOSITION de sections (titre →
     * markdown). Ne persiste rien : l'UI laisse l'utilisateur réviser/éditer
     * puis enregistrer le GameSystem via {@link #updateGameSystem}/{@link #createGameSystem}.
     */
    public RulesImportResult importRulesFromPdf(byte[] pdfBytes, String filename) {
        return rulesPdfImporter.importRules(pdfBytes, filename);
    }

    /**
     * Variante streamée de {@link #importRulesFromPdf} : remonte l'avancement via
     * callbacks (import long → l'UI affiche une progression). Ne persiste rien.
     */
    public void importRulesFromPdfStreaming(
            byte[] pdfBytes,
            String filename,
            java.util.function.Consumer<com.loremind.domain.gamesystemcontext.RulesImportProgress> onProgress,
            Runnable onHeartbeat,
            java.util.function.Consumer<String> onStatus,
            java.util.function.Consumer<RulesImportResult> onDone,
            java.util.function.Consumer<Throwable> onError) {
        rulesPdfImporter.importRulesStreaming(
                pdfBytes, filename, onProgress, onHeartbeat, onStatus, onDone, onError);
    }

    /**
     * Parameter Object pour la création / mise à jour d'un GameSystem.
     * Les templates peuvent etre null (interpretes comme listes vides).
     */
    public record GameSystemData(
            String name,
            String description,
            String rulesMarkdown,
            List<TemplateField> characterTemplate,
            List<TemplateField> npcTemplate,
            List<TemplateField> enemyTemplate,
            String foundryActorType,
            String author,
            boolean isPublic
    ) {}

    public GameSystem createGameSystem(GameSystemData data) {
        GameSystem gameSystem = GameSystem.builder()
                .name(data.name())
                .description(data.description())
                .rulesMarkdown(data.rulesMarkdown())
                .foundryActorType(normalize(data.foundryActorType()))
                .author(normalize(data.author()))
                .isPublic(data.isPublic())
                .build();
        gameSystem.replaceCharacterTemplate(data.characterTemplate());
        gameSystem.replaceNpcTemplate(data.npcTemplate());
        gameSystem.replaceEnemyTemplate(data.enemyTemplate());
        return gameSystemRepository.save(gameSystem);
    }

    public Optional<GameSystem> getGameSystemById(String id) {
        return gameSystemRepository.findById(id);
    }

    public List<GameSystem> getAllGameSystems() {
        return gameSystemRepository.findAll();
    }

    public GameSystem updateGameSystem(String id, GameSystemData data) {
        GameSystem existing = gameSystemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("GameSystem non trouvé avec l'ID: " + id));
        existing.setName(data.name());
        existing.setDescription(data.description());
        existing.setRulesMarkdown(data.rulesMarkdown());
        existing.replaceCharacterTemplate(data.characterTemplate());
        existing.replaceNpcTemplate(data.npcTemplate());
        existing.replaceEnemyTemplate(data.enemyTemplate());
        existing.setFoundryActorType(normalize(data.foundryActorType()));
        existing.setAuthor(normalize(data.author()));
        existing.setPublic(data.isPublic());
        return gameSystemRepository.save(existing);
    }

    /** Un champ scalaire d'une structure d'acteur Foundry importée. */
    public record FoundryStructField(String path, String label, String type) {}

    /**
     * Remplace le template ENNEMI par une structure importée d'un système Foundry :
     * chaque champ devient un TemplateField mappé à son chemin Foundry. Pose aussi le
     * type d'acteur. L'utilisateur élague/renomme ensuite dans l'éditeur de template.
     */
    public GameSystem importFoundryStructure(String id, String actorType, List<FoundryStructField> fields) {
        GameSystem gs = gameSystemRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("GameSystem non trouvé avec l'ID: " + id));

        List<TemplateField> template = new ArrayList<>();
        Set<String> usedNames = new HashSet<>();
        for (FoundryStructField f : fields == null ? List.<FoundryStructField>of() : fields) {
            if (f.path() == null || f.path().isBlank()) continue;
            String label = (f.label() != null && !f.label().isBlank()) ? f.label().trim() : f.path();
            // Nom unique : libellé si libre, sinon le chemin (toujours unique).
            String name = usedNames.contains(label.toLowerCase()) ? f.path() : label;
            if (usedNames.contains(name.toLowerCase())) continue;
            usedNames.add(name.toLowerCase());
            FieldType type = "number".equalsIgnoreCase(f.type()) ? FieldType.NUMBER : FieldType.TEXT;
            template.add(new TemplateField(name, type, null, null, f.path()));
        }
        gs.replaceEnemyTemplate(template);
        gs.setFoundryActorType(normalize(actorType));
        return gameSystemRepository.save(gs);
    }

    public void deleteGameSystem(String id) {
        gameSystemRepository.deleteById(id);
    }

    public boolean gameSystemExists(String id) {
        return gameSystemRepository.existsById(id);
    }

    public List<GameSystem> searchGameSystems(String query) {
        if (query == null || query.isBlank()) return List.of();
        return gameSystemRepository.searchByName(query.trim());
    }

    private String normalize(String value) {
        return (value == null || value.isBlank()) ? null : value;
    }
}
