package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Character;
import com.loremind.domain.campaigncontext.ports.CharacterRepository;
import org.springframework.stereotype.Service;

import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * Service d'application pour les fiches de personnages (PJ).
 * Les PJ appartiennent désormais à un Playthrough (Partie), pas à la Campagne.
 */
@Service
public class CharacterService {

    private final CharacterRepository characterRepository;

    public CharacterService(CharacterRepository characterRepository) {
        this.characterRepository = characterRepository;
    }

    public record CharacterData(
            String name,
            String portraitImageId,
            String headerImageId,
            Map<String, String> values,
            Map<String, List<String>> imageValues,
            Map<String, Map<String, String>> keyValueValues,
            String playthroughId,
            Integer order
    ) {}

    public Character createCharacter(CharacterData data) {
        int order = data.order() != null
                ? data.order()
                : nextOrderFor(data.playthroughId());
        Character character = Character.builder()
                .name(data.name())
                .portraitImageId(data.portraitImageId())
                .headerImageId(data.headerImageId())
                .values(data.values() != null ? new HashMap<>(data.values()) : new HashMap<>())
                .imageValues(data.imageValues() != null ? new HashMap<>(data.imageValues()) : new HashMap<>())
                .keyValueValues(data.keyValueValues() != null ? new HashMap<>(data.keyValueValues()) : new HashMap<>())
                .playthroughId(data.playthroughId())
                .order(order)
                .build();
        return characterRepository.save(character);
    }

    public Optional<Character> getCharacterById(String id) {
        return characterRepository.findById(id);
    }

    public List<Character> getCharactersByPlaythroughId(String playthroughId) {
        return characterRepository.findByPlaythroughId(playthroughId);
    }

    public Character updateCharacter(String id, CharacterData data) {
        Character existing = characterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Character non trouvé avec l'ID: " + id));
        existing.setName(data.name());
        existing.setPortraitImageId(data.portraitImageId());
        existing.setHeaderImageId(data.headerImageId());
        existing.setValues(data.values() != null ? new HashMap<>(data.values()) : new HashMap<>());
        existing.setImageValues(data.imageValues() != null ? new HashMap<>(data.imageValues()) : new HashMap<>());
        existing.setKeyValueValues(data.keyValueValues() != null ? new HashMap<>(data.keyValueValues()) : new HashMap<>());
        if (data.order() != null) {
            existing.setOrder(data.order());
        }
        // playthroughId immuable après création.
        return characterRepository.save(existing);
    }

    public void deleteCharacter(String id) {
        characterRepository.deleteById(id);
    }

    private int nextOrderFor(String playthroughId) {
        return characterRepository.findByPlaythroughId(playthroughId).stream()
                .mapToInt(Character::getOrder)
                .max()
                .orElse(-1) + 1;
    }
}
