package com.loremind.infrastructure.transfer.pdf;

import com.loremind.infrastructure.persistence.entity.*;
import com.loremind.infrastructure.persistence.jpa.*;
import org.apache.pdfbox.pdmodel.PDDocument;
import org.apache.pdfbox.text.PDFTextStripper;
import org.apache.pdfbox.text.TextPosition;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

/** Verifie le PDF rendu, sans Spring ni connexion a une base de donnees. */
class PdfEditorialLayoutTest {
    private static final String PROSE = "Le chemin traverse la foret et rejoint les portes du village. "
            + "Les voyageurs observent les traces sur le sol et interrogent les gardiens. ";

    private PdfExportService service(List<SceneJpaEntity> scenes) {
        var campaign = CampaignJpaEntity.builder().id(1L).name("Le chemin des brumes").build();
        var campaigns = mock(CampaignJpaRepository.class);
        when(campaigns.findById(1L)).thenReturn(Optional.of(campaign));
        var st = new Structure();
        st.visibleArcs = List.of(ArcJpaEntity.builder().id(2L).name("Arc").build());
        st.chaptersByArc.put(2L, List.of(ChapterJpaEntity.builder().id(3L).name("Chapitre").build()));
        st.scenesByChapter.put(3L, scenes);
        var loader = mock(PdfStructureLoader.class);
        when(loader.load(campaign)).thenReturn(st);
        return new PdfExportService(campaigns, mock(NpcJpaRepository.class), mock(EnemyJpaRepository.class),
                mock(RandomTableJpaRepository.class), mock(GameSystemJpaRepository.class),
                mock(LoreNodeJpaRepository.class), mock(PageJpaRepository.class), mock(TemplateJpaRepository.class),
                loader, mock(PdfImageEncoder.class));
    }

    @Test
    void longNarrationFlowsThroughBothColumnsWithoutLostOrDuplicatedParagraphs() throws Exception {
        String text = IntStream.range(0, 45).mapToObj(i -> "Repere%03d ".formatted(i) + PROSE.repeat(3))
                .reduce((a, b) -> a + "\n\n" + b).orElseThrow();
        var service = service(List.of(SceneJpaEntity.builder().id(4L).name("La route")
                .playerNarration(text).gmSecretNotes("CONFIDENTIEL").build()));
        try (var pdf = PDDocument.load(service.export("1"))) {
            var reader = new Positions();
            String extracted = reader.getText(pdf);
            for (int i = 0; i < 45; i++) assertEquals(1, count(extracted, "Repere%03d".formatted(i)));
            var markers = reader.lines.stream().filter(l -> l.text.startsWith("Repere")).toList();
            assertTrue(markers.stream().anyMatch(l -> l.x < 150));
            assertTrue(markers.stream().anyMatch(l -> l.x > 300));
            assertTrue(pdf.getNumberOfPages() > 3);
            assertBounds(reader, pdf);
        }
    }

    @Test
    void shortReadAloudBoxesStayTogetherAtColumnAndPageBreaks() throws Exception {
        List<SceneJpaEntity> scenes = IntStream.range(0, 14).mapToObj(i -> SceneJpaEntity.builder()
                .id((long) i + 4).name("Etape " + i)
                .playerNarration("Debut%02d ".formatted(i) + PROSE.repeat(4) + " Fin%02d".formatted(i)).build()).toList();
        try (var pdf = PDDocument.load(service(scenes).export("1"))) {
            var reader = new Positions();
            reader.getText(pdf);
            for (int i = 0; i < 14; i++) {
                String start = "Debut%02d".formatted(i), end = "Fin%02d".formatted(i);
                Line first = reader.lines.stream().filter(l -> l.text.contains(start)).findFirst().orElseThrow();
                Line last = reader.lines.stream().filter(l -> l.text.contains(end)).findFirst().orElseThrow();
                assertEquals(first.page, last.page, "Encadre coupe entre deux pages");
                assertEquals(first.x < 300, last.x < 300, "Encadre coupe entre deux colonnes");
            }
            assertBounds(reader, pdf);
        }
    }

    @Test
    void playerEditionStillOmitsSecretsAndSmallFormatKeepsOneColumn() throws Exception {
        var service = service(List.of(SceneJpaEntity.builder().id(4L).name("La route")
                .playerNarration(PROSE.repeat(8)).gmSecretNotes("CONFIDENTIEL").build()));
        for (String format : List.of("A4", "A5", "LETTER")) {
            var options = PdfExportOptions.parse("player", format, true, "NARRATIVE", null);
            try (var pdf = PDDocument.load(service.export("1", options))) {
                String text = new PDFTextStripper().getText(pdf);
                assertFalse(text.contains("CONFIDENTIEL"));
                assertTrue(text.contains("voyageurs"));
                assertEquals(format.equals("A4"), service.xhtml("1", options).contains("<div class=\"editorial-flow\">"));
            }
        }
    }

    private static int count(String text, String needle) {
        return (text.length() - text.replace(needle, "").length()) / needle.length();
    }

    private static void assertBounds(Positions reader, PDDocument pdf) {
        for (Line line : reader.lines) {
            var bounds = pdf.getPage(line.page - 1).getMediaBox();
            assertTrue(line.x >= 0 && line.right <= bounds.getWidth() + 1, line.toString());
            assertTrue(line.y >= 0 && line.y <= bounds.getHeight(), line.toString());
        }
    }

    private record Line(String text, int page, float x, float right, float y) {}

    private static class Positions extends PDFTextStripper {
        final List<Line> lines = new ArrayList<>();
        Positions() throws IOException { super(); }
        @Override
        protected void writeString(String text, List<TextPosition> positions) throws IOException {
            if (!positions.isEmpty()) {
                TextPosition first = positions.get(0), last = positions.get(positions.size() - 1);
                lines.add(new Line(text, getCurrentPageNo(), first.getXDirAdj(),
                        last.getXDirAdj() + last.getWidthDirAdj(), first.getYDirAdj()));
            }
            super.writeString(text, positions);
        }
    }
}
