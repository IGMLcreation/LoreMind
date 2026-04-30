package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.CharacterService;
import com.loremind.domain.campaigncontext.Character;
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

    public CharacterController(CharacterService characterService, CharacterMapper characterMapper) {
        this.characterService = characterService;
        this.characterMapper = characterMapper;
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

    @GetMapping("/campaign/{campaignId}")
    public ResponseEntity<List<CharacterDTO>> getCharactersByCampaign(@PathVariable String campaignId) {
        List<CharacterDTO> dtos = characterService.getCharactersByCampaignId(campaignId).stream()
                .map(characterMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

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
                dto.getCampaignId(),
                order
        );
    }
}
