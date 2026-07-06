package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.CharacterService;
import com.loremind.domain.campaigncontext.bestiary.Character;
import com.loremind.infrastructure.web.dto.campaigncontext.CharacterDTO;
import com.loremind.infrastructure.web.mapper.CharacterMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/characters")
public class CharacterController {

    private final CharacterService characterService;
    private final CharacterMapper characterMapper;
    private final com.loremind.domain.playcontext.ports.PlaythroughRepository playthroughRepository;

    public CharacterController(CharacterService characterService, CharacterMapper characterMapper,
                               com.loremind.domain.playcontext.ports.PlaythroughRepository playthroughRepository) {
        this.characterService = characterService;
        this.characterMapper = characterMapper;
        this.playthroughRepository = playthroughRepository;
    }

    @PostMapping
    public ResponseEntity<CharacterDTO> createCharacter(@RequestBody CharacterDTO dto) {
        Character created = characterService.createCharacter(toData(dto, null));
        return ResponseEntity.ok(characterMapper.toDTO(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<CharacterDTO> getCharacterById(@PathVariable String id) {
        return characterService.getCharacterById(id)
                .map(c -> ResponseEntity.ok(characterMapper.toDTO(c)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/playthrough/{playthroughId}")
    public ResponseEntity<List<CharacterDTO>> getCharactersByPlaythrough(@PathVariable String playthroughId) {
        List<CharacterDTO> dtos = characterService.getCharactersByPlaythroughId(playthroughId).stream()
                .map(characterMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    /**
     * Recherche par nom — alimente la recherche globale (Ctrl+K). Le résultat est
     * enrichi du campaignId (résolu via le Playthrough) pour que le front puisse
     * construire la route /campaigns/{c}/playthroughs/{p}/characters/{id}.
     */
    @GetMapping("/search")
    public ResponseEntity<List<CharacterSearchDTO>> search(@RequestParam("q") String query) {
        List<CharacterSearchDTO> out = characterService.searchCharacters(query).stream()
                .map(c -> new CharacterSearchDTO(
                        c.getId(),
                        c.getName(),
                        c.getPlaythroughId(),
                        c.getPlaythroughId() != null
                                ? playthroughRepository.findById(c.getPlaythroughId())
                                        .map(com.loremind.domain.playcontext.Playthrough::getCampaignId)
                                        .orElse(null)
                                : null))
                .filter(r -> r.campaignId() != null) // PJ orphelin (legacy) : non navigable → exclu
                .collect(Collectors.toList());
        return ResponseEntity.ok(out);
    }

    /** Résultat de recherche d'un PJ, enrichi pour la navigation. */
    public record CharacterSearchDTO(String id, String name, String playthroughId, String campaignId) {}

    @PutMapping("/{id}")
    public ResponseEntity<CharacterDTO> updateCharacter(@PathVariable String id, @RequestBody CharacterDTO dto) {
        Character updated = characterService.updateCharacter(id, toData(dto, dto.getOrder()));
        return ResponseEntity.ok(characterMapper.toDTO(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteCharacter(@PathVariable String id) {
        characterService.deleteCharacter(id);
        return ResponseEntity.noContent().build();
    }

    private CharacterService.CharacterData toData(CharacterDTO dto, Integer order) {
        return new CharacterService.CharacterData(
                dto.getName(),
                dto.getPortraitImageId(),
                dto.getHeaderImageId(),
                dto.getValues(),
                dto.getImageValues(),
                dto.getKeyValueValues(),
                dto.getPlaythroughId(),
                order
        );
    }
}
