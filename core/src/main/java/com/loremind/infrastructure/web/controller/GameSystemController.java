package com.loremind.infrastructure.web.controller;

import com.loremind.application.gamesystemcontext.GameSystemService;
import com.loremind.domain.gamesystemcontext.GameSystem;
import com.loremind.domain.shared.template.TemplateField;
import com.loremind.infrastructure.web.dto.gamesystemcontext.GameSystemDTO;
import com.loremind.infrastructure.web.dto.shared.TemplateFieldDTO;
import com.loremind.infrastructure.web.mapper.GameSystemMapper;
import com.loremind.infrastructure.web.mapper.TemplateFieldMapper;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/game-systems")
public class GameSystemController {

    private final GameSystemService gameSystemService;
    private final GameSystemMapper gameSystemMapper;
    private final TemplateFieldMapper templateFieldMapper;

    public GameSystemController(GameSystemService gameSystemService,
                                GameSystemMapper gameSystemMapper,
                                TemplateFieldMapper templateFieldMapper) {
        this.gameSystemService = gameSystemService;
        this.gameSystemMapper = gameSystemMapper;
        this.templateFieldMapper = templateFieldMapper;
    }

    @PostMapping
    public ResponseEntity<GameSystemDTO> createGameSystem(@RequestBody GameSystemDTO dto) {
        GameSystem created = gameSystemService.createGameSystem(toData(dto));
        return ResponseEntity.ok(gameSystemMapper.toDTO(created));
    }

    @GetMapping("/{id}")
    public ResponseEntity<GameSystemDTO> getGameSystemById(@PathVariable String id) {
        return gameSystemService.getGameSystemById(id)
                .map(g -> ResponseEntity.ok(gameSystemMapper.toDTO(g)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<GameSystemDTO>> getAllGameSystems() {
        List<GameSystemDTO> dtos = gameSystemService.getAllGameSystems().stream()
                .map(gameSystemMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @GetMapping("/search")
    public ResponseEntity<List<GameSystemDTO>> searchGameSystems(@RequestParam("q") String query) {
        List<GameSystemDTO> dtos = gameSystemService.searchGameSystems(query).stream()
                .map(gameSystemMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(dtos);
    }

    @PutMapping("/{id}")
    public ResponseEntity<GameSystemDTO> updateGameSystem(@PathVariable String id, @RequestBody GameSystemDTO dto) {
        GameSystem updated = gameSystemService.updateGameSystem(id, toData(dto));
        return ResponseEntity.ok(gameSystemMapper.toDTO(updated));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteGameSystem(@PathVariable String id) {
        gameSystemService.deleteGameSystem(id);
        return ResponseEntity.noContent().build();
    }

    /** Mappe les violations d'invariants domaine (doublons de champs, etc.) en 400. */
    @ExceptionHandler(IllegalArgumentException.class)
    public ResponseEntity<String> onIllegalArgument(IllegalArgumentException ex) {
        return ResponseEntity.status(HttpStatus.BAD_REQUEST).body(ex.getMessage());
    }

    private GameSystemService.GameSystemData toData(GameSystemDTO dto) {
        return new GameSystemService.GameSystemData(
                dto.getName(),
                dto.getDescription(),
                dto.getRulesMarkdown(),
                toDomainFields(dto.getCharacterTemplate()),
                toDomainFields(dto.getNpcTemplate()),
                dto.getAuthor(),
                dto.isPublic()
        );
    }

    private List<TemplateField> toDomainFields(List<TemplateFieldDTO> dtos) {
        if (dtos == null) return new ArrayList<>();
        List<TemplateField> out = new ArrayList<>(dtos.size());
        for (TemplateFieldDTO d : dtos) out.add(templateFieldMapper.toDomain(d));
        return out;
    }
}
