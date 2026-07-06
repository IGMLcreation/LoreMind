package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.bestiary.Character;
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
                .values(copyStringMap(data.values()))
                .imageValues(copyStringListMap(data.imageValues()))
                .keyValueValues(copyStringMapMap(data.keyValueValues()))
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
        existing.setValues(copyStringMap(data.values()));
        existing.setImageValues(copyStringListMap(data.imageValues()));
        existing.setKeyValueValues(copyStringMapMap(data.keyValueValues()));
        if (data.order() != null) {
            existing.setOrder(data.order());
        }
        // playthroughId immuable après création.
        return characterRepository.save(existing);
    }

    public void deleteCharacter(String id) {
        characterRepository.deleteById(id);
    }

    public List<Character> searchCharacters(String query) {
        if (query == null || query.isBlank()) return List.of();
        return characterRepository.searchByName(query.trim());
    }

    private int nextOrderFor(String playthroughId) {
        return characterRepository.findByPlaythroughId(playthroughId).stream()
                .mapToInt(Character::getOrder)
                .max()
                .orElse(-1) + 1;
    }

    private static Map<String, String> copyStringMap(Map<String, String> map) {
        return map != null ? new HashMap<>(map) : new HashMap<>();
    }

    private static Map<String, List<String>> copyStringListMap(Map<String, List<String>> map) {
        return map != null ? new HashMap<>(map) : new HashMap<>();
    }

    private static Map<String, Map<String, String>> copyStringMapMap(Map<String, Map<String, String>> map) {
        return map != null ? new HashMap<>(map) : new HashMap<>();
    }
}
