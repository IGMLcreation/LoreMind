package com.loremind.infrastructure.web.controller;

import com.loremind.application.campaigncontext.SceneService;
import com.loremind.domain.campaigncontext.Scene;
import com.loremind.infrastructure.web.dto.campaigncontext.SceneDTO;
import com.loremind.infrastructure.web.mapper.SceneMapper;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

/**
 * REST Controller pour le contexte Scene.
 */
@RestController
@RequestMapping("/api/scenes")
public class SceneController {

    private final SceneService sceneService;
    private final SceneMapper sceneMapper;

    public SceneController(SceneService sceneService, SceneMapper sceneMapper) {
        this.sceneService = sceneService;
        this.sceneMapper = sceneMapper;
    }

    @PostMapping
    public ResponseEntity<SceneDTO> createScene(@RequestBody SceneDTO sceneDTO) {
        Scene scene = sceneMapper.toDomain(sceneDTO);
        Scene createdScene = sceneService.createScene(scene.getName(), scene.getDescription(), scene.getChapterId(), scene.getOrder(), scene.getIcon());
        return ResponseEntity.ok(sceneMapper.toDTO(createdScene));
    }

    @GetMapping("/{id}")
    public ResponseEntity<SceneDTO> getSceneById(@PathVariable String id) {
        return sceneService.getSceneById(id)
                .map(scene -> ResponseEntity.ok(sceneMapper.toDTO(scene)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping
    public ResponseEntity<List<SceneDTO>> getAllScenes(
            @RequestParam(value = "chapterId", required = false) String chapterId) {
        List<Scene> scenes = (chapterId != null && !chapterId.isBlank())
                ? sceneService.getScenesByChapterId(chapterId)
                : sceneService.getAllScenes();
        List<SceneDTO> sceneDTOs = scenes.stream()
                .map(sceneMapper::toDTO)
                .collect(Collectors.toList());
        return ResponseEntity.ok(sceneDTOs);
    }

    @PutMapping("/{id}")
    public ResponseEntity<SceneDTO> updateScene(@PathVariable String id, @RequestBody SceneDTO sceneDTO) {
        Scene updatedScene = sceneService.updateScene(id, sceneMapper.toDomain(sceneDTO));
        return ResponseEntity.ok(sceneMapper.toDTO(updatedScene));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteScene(@PathVariable String id) {
        sceneService.deleteScene(id);
        return ResponseEntity.noContent().build();
    }
}
