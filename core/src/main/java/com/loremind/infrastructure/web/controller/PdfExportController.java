package com.loremind.infrastructure.web.controller;

import com.loremind.infrastructure.transfer.pdf.PdfExportOptions;
import com.loremind.infrastructure.transfer.pdf.PdfExportService;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.server.ResponseStatusException;

import java.util.NoSuchElementException;

/**
 * Export d'une campagne en livret PDF.
 * <p>
 * {@code GET /api/campaigns/{campaignId}/pdf-export} -> application/pdf (structure
 * narrative + PNJ/ennemis + lore + battlemaps).
 * <p>
 * Options (toutes facultatives, defaut = livret MJ complet en A4) :
 * {@code edition=gm|player}, {@code format=A4|A5|LETTER}, {@code print=true} (sans aplats),
 * {@code sections=NARRATIVE,QUESTS,NPCS,ENEMIES,TABLES,LORE}, {@code lore=ALL|LINKED}.
 */
@RestController
@RequestMapping("/api/campaigns/{campaignId}/pdf-export")
public class PdfExportController {

    private final PdfExportService pdfExportService;

    public PdfExportController(PdfExportService pdfExportService) {
        this.pdfExportService = pdfExportService;
    }

    @GetMapping(produces = MediaType.APPLICATION_PDF_VALUE)
    public ResponseEntity<byte[]> export(@PathVariable String campaignId,
                                         @RequestParam(required = false) String edition,
                                         @RequestParam(required = false) String format,
                                         @RequestParam(required = false) Boolean print,
                                         @RequestParam(required = false) String sections,
                                         @RequestParam(required = false) String lore) {
        PdfExportOptions options = PdfExportOptions.parse(edition, format, print, sections, lore);
        byte[] pdf;
        String name;
        try {
            name = pdfExportService.campaignName(campaignId);
            pdf = pdfExportService.export(campaignId, options);
        } catch (NoSuchElementException e) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, e.getMessage());
        }

        String filename = slug(name) + (options.gmContent() ? "" : "_joueurs") + ".pdf";
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"" + filename + "\"")
                .contentType(MediaType.APPLICATION_PDF)
                .body(pdf);
    }

    /** Nom de fichier sur : alphanum + tirets, le reste en "_". */
    private static String slug(String name) {
        if (name == null || name.isBlank()) return "campagne";
        String s = name.trim().replaceAll("[^a-zA-Z0-9-_]+", "_").replaceAll("_+", "_");
        // Les runs de "_" sont deja fusionnes ci-dessus : au plus un "_" de bord.
        // On le retire par manipulation de chaine (pas de regex ancree : ni S5850 ni S5852).
        if (s.startsWith("_")) s = s.substring(1);
        if (s.endsWith("_")) s = s.substring(0, s.length() - 1);
        return s.isBlank() ? "campagne" : s;
    }
}
