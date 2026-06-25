package com.loremind.infrastructure.web.controller;

import com.loremind.infrastructure.transfer.foundry.FoundryExportService;
import com.loremind.infrastructure.transfer.foundry.FoundryExportService.BuiltBundle;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.time.Instant;
import java.util.NoSuchElementException;

/**
 * Export d'une campagne vers un bundle Foundry VTT (cf. docs/foundry-bundle-schema.md).
 * <p>
 * {@code GET /api/campaigns/{campaignId}/foundry-export} -> .zip (manifest + data.json
 * + assets/). Le bundle assemble (metadonnees) est construit en synchrone pour pouvoir
 * renvoyer 404 immediatement ; seuls les binaires sont streames ensuite.
 */
@RestController
@RequestMapping("/api/campaigns/{campaignId}/foundry-export")
public class FoundryExportController {

    private final FoundryExportService foundryExportService;

    public FoundryExportController(FoundryExportService foundryExportService) {
        this.foundryExportService = foundryExportService;
    }

    @GetMapping(produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> export(@PathVariable String campaignId) {
        String exportedAt = Instant.now().toString();
        BuiltBundle bundle;
        try {
            bundle = foundryExportService.buildBundle(campaignId, exportedAt);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }

        String filename = slug(bundle.manifest().campaignName()) + "-foundry.zip";
        StreamingResponseBody body = out -> foundryExportService.writeZip(bundle, out);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(body);
    }

    /** Nom de fichier sur : alphanum + tirets, le reste en "_". */
    private static String slug(String name) {
        if (name == null || name.isBlank()) return "campagne";
        String s = name.trim().replaceAll("[^a-zA-Z0-9-_]+", "_").replaceAll("_+", "_");
        s = s.replaceAll("^_|_$", "");
        return s.isBlank() ? "campagne" : s;
    }
}
