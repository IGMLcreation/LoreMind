package com.loremind.infrastructure.web.controller;

import com.loremind.infrastructure.transfer.pdf.PdfExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;

/**
 * Export d'une campagne en livret PDF.
 * <p>
 * {@code GET /api/campaigns/{campaignId}/pdf-export} -> application/pdf (structure
 * narrative + PNJ/ennemis + lore + battlemaps).
 */
@RestController
@RequestMapping("/api/campaigns/{campaignId}/pdf-export")
public class PdfExportController {

    private final PdfExportService pdfExportService;

    public PdfExportController(PdfExportService pdfExportService) {
        this.pdfExportService = pdfExportService;
    }

    @GetMapping(produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> export(@PathVariable String campaignId) {
        byte[] pdf;
        String name;
        try {
            name = pdfExportService.campaignName(campaignId);
            pdf = pdfExportService.export(campaignId);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }

        String filename = slug(name) + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /** Nom de fichier sur : alphanum + tirets, le reste en "_". */
    private static String slug(String name) {
        if (name == null || name.isBlank()) return "campagne";
        String s = name.trim().replaceAll("[^a-zA-Z0-9-_]+", "_").replaceAll("_+", "_");
        s = s.replaceAll("^_|_$", "");
        return s.isBlank() ? "campagne" : s;
    }
}
