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
 */
@Service
public class CharacterService {

    private final CharacterRepository characterRepository;

    public CharacterService(CharacterRepository characterRepository) {
        this.characterRepository = characterRepository;
    }

    /**
     * Parameter Object pour la création / mise à jour d'un Character.
     * `order` est fourni par le controller ; si absent, le service le calcule.
     * Les maps {@code values}/{@code imageValues} peuvent etre null (interpretees vides).
     */
    public record CharacterData(
            String name,
            String portraitImageId,
            String headerImageId,
            Map<String, String> values,
            Map<String, List<String>> imageValues,
            String campaignId,
            Integer order
    ) {}

    public Character createCharacter(CharacterData data) {
        int order = data.order() != null
                ? data.order()
                : nextOrderFor(data.campaignId());
        Character character = Character.builder()
                .name(data.name())
                .portraitImageId(data.portraitImageId())
                .headerImageId(data.headerImageId())
                .values(data.values() != null ? new HashMap<>(data.values()) : new HashMap<>())
                .imageValues(data.imageValues() != null ? new HashMap<>(data.imageValues()) : new HashMap<>())
                .campaignId(data.campaignId())
                .order(order)
                .build();
        return characterRepository.save(character);
    }

    public Optional<Character> getCharacterById(String id) {
        return characterRepository.findById(id);
    }

    public List<Character> getCharactersByCampaignId(String campaignId) {
        return characterRepository.findByCampaignId(campaignId);
    }

    public Character updateCharacter(String id, CharacterData data) {
        Character existing = characterRepository.findById(id)
                .orElseThrow(() -> new IllegalArgumentException("Character non trouvé avec l'ID: " + id));
        existing.setName(data.name());
        existing.setPortraitImageId(data.portraitImageId());
        existing.setHeaderImageId(data.headerImageId());
        existing.setValues(data.values() != null ? new HashMap<>(data.values()) : new HashMap<>());
        existing.setImageValues(data.imageValues() != null ? new HashMap<>(data.imageValues()) : new HashMap<>());
        if (data.order() != null) {
            existing.setOrder(data.order());
        }
        // campaignId n'est pas modifiable après création (cross-campagne move hors scope MVP).
        return characterRepository.save(existing);
    }

    public void deleteCharacter(String id) {
        characterRepository.deleteById(id);
    }

    /** Renvoie la prochaine position libre — append en fin de liste. */
    private int nextOrderFor(String campaignId) {
        return characterRepository.findByCampaignId(campaignId).stream()
                .mapToInt(Character::getOrder)
                .max()
                .orElse(-1) + 1;
    }
}
