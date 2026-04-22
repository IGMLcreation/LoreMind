package com.loremind.application.campaigncontext;

import com.loremind.domain.campaigncontext.Character;
import com.loremind.domain.campaigncontext.ports.CharacterRepository;
import org.springframework.stereotype.Service;

import java.util.List;
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
     */
    public record CharacterData(String name, String markdownContent, String campaignId, Integer order) {}

    public Character createCharacter(CharacterData data) {
        int order = data.order() != null
                ? data.order()
                : nextOrderFor(data.campaignId());
        Character character = Character.builder()
                .name(data.name())
                .markdownContent(data.markdownContent())
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
        existing.setMarkdownContent(data.markdownContent());
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
