package com.loremind.infrastructure.transfer.pdf;

import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.pdmodel.PDPageContentStream;
import org.apache.pdfbox.util.Matrix;

import java.awt.Color;
import java.io.IOException;

/** Motifs vectoriels originaux, confines aux 49 points de marge inferieure des pages A4.
 * Les dessiner apres la pagination conserve les renvois, le texte et la mise en page. */
final class PdfPageOrnaments {
    private static final Color INK = new Color(0x34594f);
    private static final Color SAGE = new Color(0xa8bbae);
    private static final Color PALE = new Color(0xe6ece3);
    private static final Color GOLD = new Color(0xb5a278);
    private static final Color PRINT = new Color(0x999999);

    private PdfPageOrnaments() {}

    static void draw(PDDocument document, PdfExportOptions options) throws IOException {
        if (options.format() != PdfExportOptions.PageFormat.A4) return;
        // Pas de decoration sur la couverture, ni sur les annexes cartographiques paysage.
        for (int i = 1; i < document.getNumberOfPages(); i++) {
            var page = document.getPage(i);
            var bounds = page.getMediaBox();
            if (bounds.getWidth() > bounds.getHeight()) continue;
            try (var canvas = new PDPageContentStream(document, page,
                    PDPageContentStream.AppendMode.PREPEND, true, true)) {
                canvas.saveGraphicsState();
                canvas.addRect(0, 0, bounds.getWidth(), 49);
                canvas.clip();
                // Le coin exterieur est plus ample ; alternance pour les pages en vis-a-vis.
                corner(canvas, 0, false, i % 2 == 1 ? 1f : .72f, options.printFriendly());
                corner(canvas, bounds.getWidth(), true, i % 2 == 0 ? 1f : .72f, options.printFriendly());
                folio(canvas, bounds.getWidth() / 2, options.printFriendly());
                canvas.restoreGraphicsState();
            }
        }
    }

    private static void corner(PDPageContentStream c, float x, boolean mirror, float scale,
                               boolean print) throws IOException {
        c.saveGraphicsState();
        c.transform(new Matrix(mirror ? -scale : scale, 0, 0, scale, x, 0));
        if (!print) {
            c.setNonStrokingColor(PALE);
            c.moveTo(0, 49);
            c.curveTo(35, 18, 94, 9, 184, 0);
            c.lineTo(0, 0);
            c.closePath();
            c.fill();
            c.setNonStrokingColor(SAGE);
            c.moveTo(0, 35);
            c.curveTo(40, 8, 81, 8, 150, 0);
            c.lineTo(0, 0);
            c.closePath();
            c.fill();
            c.setNonStrokingColor(INK);
            c.moveTo(0, 21);
            c.curveTo(22, 9, 63, 3, 110, 0);
            c.lineTo(0, 0);
            c.closePath();
            c.fill();
            c.setNonStrokingColor(SAGE);
            for (int col = 0; col < 17; col++) {
                float dx = 14 + col * 8;
                for (int row = 0; row < 4; row++) {
                    float dy = 43 - col * 1.6f - row * 6;
                    if (dy > 13) dot(c, dx + (row % 2) * 3, dy, .35f + row * .12f);
                }
            }
        }
        c.setStrokingColor(print ? PRINT : GOLD);
        c.setLineWidth(.45f);
        c.moveTo(0, 43);
        c.curveTo(32, 16, 85, 7, 170, 3);
        c.stroke();
        c.restoreGraphicsState();
    }

    private static void folio(PDPageContentStream c, float center, boolean print) throws IOException {
        c.setStrokingColor(print ? PRINT : GOLD);
        c.setNonStrokingColor(print ? PRINT : GOLD);
        c.setLineWidth(.4f);
        for (int side : new int[]{-1, 1}) {
            c.moveTo(center + side * 18, 27);
            c.lineTo(center + side * 37, 27);
            c.stroke();
            float x = center + side * 43;
            c.moveTo(x, 29);
            c.lineTo(x + 2, 27);
            c.lineTo(x, 25);
            c.lineTo(x - 2, 27);
            c.closePath();
            if (print) c.stroke(); else c.fill();
        }
    }

    private static void dot(PDPageContentStream c, float x, float y, float r) throws IOException {
        float k = r * .552285f;
        c.moveTo(x + r, y);
        c.curveTo(x + r, y + k, x + k, y + r, x, y + r);
        c.curveTo(x - k, y + r, x - r, y + k, x - r, y);
        c.curveTo(x - r, y - k, x - k, y - r, x, y - r);
        c.curveTo(x + k, y - r, x + r, y - k, x + r, y);
        c.closePath();
        c.fill();
    }
}
