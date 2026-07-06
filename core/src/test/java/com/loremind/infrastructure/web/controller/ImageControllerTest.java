package com.loremind.infrastructure.web.controller;

import com.loremind.application.images.ImageService;
import com.loremind.domain.images.Image;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.web.servlet.MockMvc;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.time.LocalDateTime;
import java.time.Month;
import java.util.Optional;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.multipart;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.header;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * Tests d'integration pour {@link ImageController} (Shared Kernel images).
 * <p>
 * Le {@link ImageService} est mocke : il orchestre sinon MinIO (binaire) +
 * Postgres (metadonnees) via ses deux ports. On evite ainsi tout acces a MinIO,
 * indisponible en test, tout en couvrant le mapping HTTP du controleur
 * (upload / metadata / content streaming / delete + cas 400 / 404).
 */
@SpringBootTest
@AutoConfigureMockMvc
class ImageControllerTest {

    @Autowired private MockMvc mockMvc;

    @MockitoBean private ImageService imageService;

    private Image sampleImage() {
        return Image.builder()
                .id("img-1")
                .filename("portrait.png")
                .contentType("image/png")
                .sizeBytes(3)
                .storageKey("images/img-1.png")
                .uploadedAt(LocalDateTime.of(2026, Month.JANUARY, 1, 12, 0))
                .build();
    }

    private MockMultipartFile pngFile(byte[] bytes) {
        return new MockMultipartFile("file", "portrait.png", "image/png", bytes);
    }

    // --- POST /api/images --------------------------------------------------

    @Test
    void upload_returns200_withMetadata() throws Exception {
        when(imageService.upload(eq("portrait.png"), eq("image/png"), any(InputStream.class), anyLong()))
                .thenReturn(sampleImage());

        mockMvc.perform(multipart("/api/images").file(pngFile(new byte[]{1, 2, 3})))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("img-1"))
                .andExpect(jsonPath("$.filename").value("portrait.png"))
                .andExpect(jsonPath("$.contentType").value("image/png"))
                .andExpect(jsonPath("$.sizeBytes").value(3))
                .andExpect(jsonPath("$.url").value("/api/images/img-1/content"));
    }

    @Test
    void upload_returns400_whenEmpty() throws Exception {
        mockMvc.perform(multipart("/api/images").file(pngFile(new byte[0])))
                .andExpect(status().isBadRequest());
    }

    @Test
    void upload_returns400_whenServiceRejects() throws Exception {
        // Validation metier (MIME non autorise, taille...) -> IllegalArgumentException.
        when(imageService.upload(any(), any(), any(InputStream.class), anyLong()))
                .thenThrow(new IllegalArgumentException("Type de fichier non supporte."));

        mockMvc.perform(multipart("/api/images")
                        .file(new MockMultipartFile("file", "evil.exe", "application/octet-stream",
                                new byte[]{1, 2, 3})))
                .andExpect(status().isBadRequest());
    }

    // --- GET /api/images/{id} ----------------------------------------------

    @Test
    void getMetadata_returns200() throws Exception {
        when(imageService.getById("img-1")).thenReturn(Optional.of(sampleImage()));

        mockMvc.perform(get("/api/images/{id}", "img-1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.id").value("img-1"))
                .andExpect(jsonPath("$.url").value("/api/images/img-1/content"));
    }

    @Test
    void getMetadata_returns404_whenMissing() throws Exception {
        when(imageService.getById("nope")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/images/{id}", "nope"))
                .andExpect(status().isNotFound());
    }

    // --- GET /api/images/{id}/content --------------------------------------

    @Test
    void getContent_returns200_withBinary() throws Exception {
        byte[] data = {10, 20, 30};
        when(imageService.getById("img-1")).thenReturn(Optional.of(sampleImage()));
        when(imageService.downloadById("img-1"))
                .thenReturn(Optional.of(new ByteArrayInputStream(data)));

        mockMvc.perform(get("/api/images/{id}/content", "img-1"))
                .andExpect(status().isOk())
                .andExpect(header().string("Content-Type", "image/png"))
                .andExpect(header().string("Cross-Origin-Resource-Policy", "cross-origin"))
                .andExpect(content().bytes(data));
    }

    @Test
    void getContent_returns404_whenMetadataMissing() throws Exception {
        when(imageService.getById("nope")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/images/{id}/content", "nope"))
                .andExpect(status().isNotFound());
    }

    @Test
    void getContent_returns404_whenBinaryLost() throws Exception {
        // Metadonnees presentes mais binaire absent (incoherence) -> 404.
        when(imageService.getById("img-1")).thenReturn(Optional.of(sampleImage()));
        when(imageService.downloadById("img-1")).thenReturn(Optional.empty());

        mockMvc.perform(get("/api/images/{id}/content", "img-1"))
                .andExpect(status().isNotFound());
    }

    // --- DELETE /api/images/{id} -------------------------------------------

    @Test
    void delete_returns204() throws Exception {
        mockMvc.perform(delete("/api/images/{id}", "img-1"))
                .andExpect(status().isNoContent());
        verify(imageService).deleteById("img-1");
    }
}
