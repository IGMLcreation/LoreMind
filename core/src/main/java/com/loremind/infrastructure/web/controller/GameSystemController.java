package com.loremind.infrastructure.web.controller;

import com.loremind.application.gamesystemcontext.GameSystemService;
import com.loremind.domain.gamesystemcontext.GameSystem;
import com.loremind.infrastructure.web.dto.gamesystemcontext.GameSystemDTO;
import com.loremind.infrastructure.web.mapper.GameSystemMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/game-systems")
public class GameSystemController {

    private final GameSystemService gameSystemService;
    private final GameSystemMapper gameSystemMapper;

    public GameSystemController(GameSystemService gameSystemService, GameSystemMapper gameSystemMapper) {
        this.gameSystemService = gameSystemService;
        this.gameSystemMapper = gameSystemMapper;
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

    private GameSystemService.GameSystemData toData(GameSystemDTO dto) {
        return new GameSystemService.GameSystemData(
                dto.getName(),
                dto.getDescription(),
                dto.getRulesMarkdown(),
                dto.getAuthor(),
                dto.isPublic()
        );
    }
}
