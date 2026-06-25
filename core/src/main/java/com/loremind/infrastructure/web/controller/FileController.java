package com.loremind.infrastructure.web.controller;

import com.loremind.application.files.StoredFileService;
import com.loremind.domain.files.StoredFile;
import com.loremind.infrastructure.web.dto.files.StoredFileDTO;
import org.springframework.core.io.InputStreamResource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Optional;

/**
 * REST Controller pour les fichiers generiques (battlemaps : media + sidecar JSON).
 * <p>
 * Expose :
 *  - POST   /api/files              (multipart/form-data, champ "file")
 *  - GET    /api/files/{id}         (metadonnees JSON)
 *  - GET    /api/files/{id}/content (binaire)
 *  - DELETE /api/files/{id}
 */
@RestController
@RequestMapping("/api/files")
public class FileController {

    private final StoredFileService fileService;

    public FileController(StoredFileService fileService) {
        this.fileService = fileService;
    }

    @PostMapping
    public ResponseEntity<StoredFileDTO> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try (InputStream in = file.getInputStream()) {
            StoredFile saved = fileService.upload(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    in,
                    file.getSize()
            );
            return ResponseEntity.ok(toDTO(saved));
        } catch (IllegalArgumentException ex) {
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<StoredFileDTO> getMetadata(@PathVariable String id) {
        return fileService.getById(id)
                .map(f -> ResponseEntity.ok(toDTO(f)))
                .orElse(ResponseEntity.notFound().build());
    }

    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> getContent(@PathVariable String id) {
        Optional<StoredFile> metadata = fileService.getById(id);
        if (metadata.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        StoredFile file = metadata.get();
        InputStream stream = fileService.downloadById(id).orElse(null);
        if (stream == null) {
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(file.getContentType()))
                .contentLength(file.getSizeBytes())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .header("Cross-Origin-Resource-Policy", "cross-origin")
                .body(new InputStreamResource(stream));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        fileService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // --- Mapping -----------------------------------------------------------

    private StoredFileDTO toDTO(StoredFile f) {
        StoredFileDTO dto = new StoredFileDTO();
        dto.setId(f.getId());
        dto.setFilename(f.getFilename());
        dto.setContentType(f.getContentType());
        dto.setSizeBytes(f.getSizeBytes());
        dto.setUrl("/api/files/" + f.getId() + "/content");
        dto.setUploadedAt(f.getUploadedAt());
        return dto;
    }
}
