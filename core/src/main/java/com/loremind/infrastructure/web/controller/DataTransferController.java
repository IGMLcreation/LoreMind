package com.loremind.infrastructure.web.controller;

import com.loremind.infrastructure.transfer.ExportService;
import com.loremind.infrastructure.transfer.ImportResult;
import com.loremind.infrastructure.transfer.ImportService;
import com.loremind.infrastructure.transfer.dto.ContentExport;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.servlet.mvc.method.annotation.StreamingResponseBody;

import java.io.IOException;
import java.io.UncheckedIOException;
import java.time.Instant;

/**
 * Endpoints d'EXPORT / IMPORT du "contenu" (admin).
 * <p>
 * - {@code GET  /api/admin/data/export} : telecharge un .zip portable.<br>
 * - {@code POST /api/admin/data/import} : importe un .zip en mode FUSION.
 * <p>
 * Garde le mode demo coherent avec {@code SettingsController} : ces operations
 * sont desactivees en demo (donnees partagees, pas d'ecriture massive).
 */
@RestController
@RequestMapping("/api/admin/data")
public class DataTransferController {

    private final ExportService exportService;
    private final ImportService importService;
    private final boolean demoMode;

    public DataTransferController(ExportService exportService,
                                 ImportService importService,
                                 @Value("${app.demo-mode:false}") boolean demoMode) {
        this.exportService = exportService;
        this.importService = importService;
        this.demoMode = demoMode;
    }

    @GetMapping(value = "/export", produces = "application/zip")
    public ResponseEntity<StreamingResponseBody> export() {
        guardDemoMode();
        // Stamp de l'horodatage cote requete (PAS dans un init statique).
        String exportedAt = Instant.now().toString();
        ContentExport content = exportService.buildExport(exportedAt);

        StreamingResponseBody body = out -> exportService.writeZip(content, out);

        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename=\"loremind-export.zip\"")
                .contentType(MediaType.parseMediaType("application/zip"))
                .body(body);
    }

    @PostMapping(value = "/import", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<ImportResult> importData(@RequestParam("file") MultipartFile file) {
        guardDemoMode();
        if (file == null || file.isEmpty()) {
            throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "Fichier d'import vide");
        }
        try {
            ImportResult result = importService.importZip(file.getInputStream());
            return ResponseEntity.ok(result);
        } catch (IOException e) {
            throw new UncheckedIOException("Echec de lecture du fichier d'import", e);
        }
    }

    private void guardDemoMode() {
        if (demoMode) {
            throw new ResponseStatusException(HttpStatus.FORBIDDEN, "Data transfer disabled in demo mode");
        }
    }
}
