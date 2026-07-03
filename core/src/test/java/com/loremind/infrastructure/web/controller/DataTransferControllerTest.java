package com.loremind.infrastructure.web.controller;

import com.loremind.infrastructure.transfer.ExportRequest;
import com.loremind.infrastructure.transfer.ExportService;
import com.loremind.infrastructure.transfer.ImportService;
import com.loremind.infrastructure.transfer.dto.ContentExport;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.http.HttpStatus;
import org.springframework.web.server.ResponseStatusException;

import java.util.List;
import java.util.NoSuchElementException;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Test UNITAIRE (sans Spring) de {@link DataTransferController} : on vérifie que les
 * paramètres de requête sont bien traduits en {@link ExportRequest} (sauvegarde complète
 * vs ciblée, passage des trois drapeaux) et le mapping des codes HTTP (404 campagne
 * inconnue, 403 mode démo). Pattern « controller instancié à la main + services mockés »
 * (cf. CampaignImportControllerUnitTest).
 */
class DataTransferControllerTest {

    private final ExportService exportService = mock(ExportService.class);
    private final ImportService importService = mock(ImportService.class);
    private final DataTransferController controller =
            new DataTransferController(exportService, importService, false);

    private static ContentExport emptyExport(String scope) {
        return new ContentExport(
                new ContentExport.Manifest(2, "dev", "t", scope),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of(), List.of(), List.of(), List.of(), List.of(),
                List.of(), List.of(), List.of());
    }

    private ArgumentCaptor<ExportRequest> captureExportAfter(Runnable call) {
        when(exportService.buildExport(anyString(), any(ExportRequest.class)))
                .thenReturn(emptyExport("complète"));
        call.run();
        ArgumentCaptor<ExportRequest> cap = ArgumentCaptor.forClass(ExportRequest.class);
        verify(exportService).buildExport(anyString(), cap.capture());
        return cap;
    }

    @Test
    void export_noCampaignId_buildsFullRequest() {
        ExportRequest req = captureExportAfter(() -> controller.export(null, true, true, true)).getValue();
        assertTrue(req.isFull());
        assertNull(req.campaignId());
    }

    @Test
    void export_withParams_mapsCampaignAndAllFlags() {
        ExportRequest req = captureExportAfter(() -> controller.export(5L, false, false, false)).getValue();
        assertFalse(req.isFull());
        assertEquals(5L, req.campaignId());
        assertFalse(req.includeLore());
        assertFalse(req.includePlay());
        assertFalse(req.includeImages());
    }

    @Test
    void export_targetedWithDefaults_keepsFlagsTrue() {
        ExportRequest req = captureExportAfter(() -> controller.export(7L, true, true, true)).getValue();
        assertEquals(7L, req.campaignId());
        assertTrue(req.includeLore());
        assertTrue(req.includePlay());
        assertTrue(req.includeImages());
    }

    @Test
    void export_unknownCampaign_maps404() {
        when(exportService.buildExport(anyString(), any(ExportRequest.class)))
                .thenThrow(new NoSuchElementException("Campagne introuvable : 999"));
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> controller.export(999L, true, true, true));
        assertEquals(HttpStatus.NOT_FOUND, ex.getStatusCode());
    }

    @Test
    void export_demoMode_forbidden() {
        DataTransferController demo = new DataTransferController(exportService, importService, true);
        ResponseStatusException ex = assertThrows(ResponseStatusException.class,
                () -> demo.export(null, true, true, true));
        assertEquals(HttpStatus.FORBIDDEN, ex.getStatusCode());
    }
}
