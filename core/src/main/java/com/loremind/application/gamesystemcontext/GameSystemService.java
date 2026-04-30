package com.loremind.application.gamesystemcontext;

import com.loremind.domain.gamesystemcontext.GameSystem;
import com.loremind.domain.gamesystemcontext.ports.GameSystemRepository;
import com.loremind.domain.shared.template.TemplateField;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Optional;

@Service
public class GameSystemService {

    private final GameSystemRepository gameSystemRepository;

    public GameSystemService(GameSystemRepository gameSystemRepository) {
        this.gameSystemRepository = gameSystemRepository;
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
            String author,
            boolean isPublic
    ) {}

    public GameSystem createGameSystem(GameSystemData data) {
        GameSystem gameSystem = GameSystem.builder()
                .name(data.name())
                .description(data.description())
                .rulesMarkdown(data.rulesMarkdown())
                .author(normalize(data.author()))
                .isPublic(data.isPublic())
                .build();
        gameSystem.replaceCharacterTemplate(data.characterTemplate());
        gameSystem.replaceNpcTemplate(data.npcTemplate());
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
        existing.setAuthor(normalize(data.author()));
        existing.setPublic(data.isPublic());
        return gameSystemRepository.save(existing);
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
