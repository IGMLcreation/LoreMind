package com.loremind.infrastructure.web.controller;

import com.loremind.application.images.ImageService;
import com.loremind.domain.images.Image;
import com.loremind.infrastructure.web.dto.images.ImageDTO;
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
 * REST Controller pour le Shared Kernel images.
 * <p>
 * Expose :
 *  - POST   /api/images              (multipart/form-data, champ "file")
 *  - GET    /api/images/{id}         (metadonnees JSON)
 *  - GET    /api/images/{id}/content (binaire, pour <img src=...>)
 *  - DELETE /api/images/{id}
 */
@RestController
@RequestMapping("/api/images")
public class ImageController {

    private final ImageService imageService;

    public ImageController(ImageService imageService) {
        this.imageService = imageService;
    }

    @PostMapping
    public ResponseEntity<ImageDTO> upload(@RequestParam("file") MultipartFile file) throws IOException {
        if (file.isEmpty()) {
            return ResponseEntity.badRequest().build();
        }
        try (InputStream in = file.getInputStream()) {
            Image saved = imageService.upload(
                    file.getOriginalFilename(),
                    file.getContentType(),
                    in,
                    file.getSize()
            );
            return ResponseEntity.ok(toDTO(saved));
        } catch (IllegalArgumentException ex) {
            // Validation metier : MIME non autorise, fichier vide, taille excessive...
            return ResponseEntity.badRequest().body(null);
        }
    }

    @GetMapping("/{id}")
    public ResponseEntity<ImageDTO> getMetadata(@PathVariable String id) {
        return imageService.getById(id)
                .map(img -> ResponseEntity.ok(toDTO(img)))
                .orElse(ResponseEntity.notFound().build());
    }

    /**
     * Streaming du binaire. Le navigateur pourra directement l'utiliser dans
     * une balise <img src="/api/images/42/content">.
     */
    @GetMapping("/{id}/content")
    public ResponseEntity<InputStreamResource> getContent(@PathVariable String id) {
        Optional<Image> metadata = imageService.getById(id);
        if (metadata.isEmpty()) {
            return ResponseEntity.notFound().build();
        }
        Image img = metadata.get();
        InputStream stream = imageService.downloadById(id).orElse(null);
        if (stream == null) {
            // Metadonnees presentes mais binaire perdu -> incoherence, on renvoie 404.
            return ResponseEntity.notFound().build();
        }
        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(img.getContentType()))
                .contentLength(img.getSizeBytes())
                .header(HttpHeaders.CACHE_CONTROL, "public, max-age=31536000, immutable")
                .body(new InputStreamResource(stream));
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable String id) {
        imageService.deleteById(id);
        return ResponseEntity.noContent().build();
    }

    // --- Mapping -----------------------------------------------------------

    private ImageDTO toDTO(Image img) {
        ImageDTO dto = new ImageDTO();
        dto.setId(img.getId());
        dto.setFilename(img.getFilename());
        dto.setContentType(img.getContentType());
        dto.setSizeBytes(img.getSizeBytes());
        dto.setUrl("/api/images/" + img.getId() + "/content");
        dto.setUploadedAt(img.getUploadedAt());
        return dto;
    }
}
