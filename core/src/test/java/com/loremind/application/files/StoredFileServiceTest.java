package com.loremind.application.files;

import com.loremind.domain.files.StoredFile;
import com.loremind.domain.files.ports.FileStorage;
import com.loremind.domain.files.ports.StoredFileRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

/**
 * Test unitaire pour StoredFileService.
 * Specificites vs ImageService : accepte video + JSON, tolere un content-type
 * absent (defaut application/octet-stream), plafond a 128 Mo.
 */
@ExtendWith(MockitoExtension.class)
class StoredFileServiceTest {

    @Mock private StoredFileRepository repository;
    @Mock private FileStorage storage;

    @InjectMocks private StoredFileService service;

    private InputStream data;

    @BeforeEach
    void setUp() {
        data = new ByteArrayInputStream(new byte[]{1, 2, 3});
    }

    @Test
    void upload_video_persistsMetadata() {
        when(storage.upload(eq("donjon.mp4"), eq("video/mp4"), any(), eq(2048L)))
                .thenReturn("files/abc.mp4");
        when(repository.save(any(StoredFile.class))).thenAnswer(inv -> {
            StoredFile f = inv.getArgument(0);
            f.setId("file-1");
            return f;
        });

        StoredFile result = service.upload("donjon.mp4", "video/mp4", data, 2048L);

        assertEquals("file-1", result.getId());
        assertEquals("files/abc.mp4", result.getStorageKey());
        assertNotNull(result.getUploadedAt());

        ArgumentCaptor<StoredFile> captor = ArgumentCaptor.forClass(StoredFile.class);
        verify(repository).save(captor.capture());
        assertEquals("video/mp4", captor.getValue().getContentType());
    }

    @Test
    void upload_jsonSidecar_accepted() {
        when(storage.upload(anyString(), anyString(), any(), anyLong())).thenReturn("k");
        when(repository.save(any(StoredFile.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> service.upload("map.dd2vtt", "application/json", data, 100L));
    }

    @Test
    void upload_nullContentType_defaultsToOctetStreamAndAccepted() {
        when(storage.upload(anyString(), eq("application/octet-stream"), any(), anyLong()))
                .thenReturn("files/k");
        when(repository.save(any(StoredFile.class))).thenAnswer(inv -> inv.getArgument(0));

        StoredFile r = service.upload("map.dd2vtt", null, data, 100L);

        assertEquals("application/octet-stream", r.getContentType());
    }

    @Test
    void upload_unsupportedMime_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> service.upload("a.html", "text/html", data, 100L));
        verifyNoInteractions(storage);
    }

    @Test
    void upload_blankFilename_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> service.upload("  ", "video/mp4", data, 100L));
        verifyNoInteractions(storage);
        verifyNoInteractions(repository);
    }

    @Test
    void upload_zeroSize_throws() {
        assertThrows(IllegalArgumentException.class,
                () -> service.upload("a.mp4", "video/mp4", data, 0L));
    }

    @Test
    void upload_tooLarge_throws() {
        long tooBig = 128L * 1024 * 1024 + 1;
        assertThrows(IllegalArgumentException.class,
                () -> service.upload("a.mp4", "video/mp4", data, tooBig));
        verifyNoInteractions(storage);
    }

    @Test
    void upload_dbFailure_compensatesByDeletingBinary() {
        when(storage.upload(anyString(), anyString(), any(), anyLong()))
                .thenReturn("files/orphan.mp4");
        when(repository.save(any(StoredFile.class))).thenThrow(new RuntimeException("DB down"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> service.upload("a.mp4", "video/mp4", data, 500L));
        assertEquals("DB down", ex.getMessage());
        verify(storage).delete("files/orphan.mp4");
    }

    @Test
    void downloadById_found_returnsStream() {
        StoredFile f = StoredFile.builder().id("file-1").storageKey("files/k.mp4").build();
        InputStream stream = new ByteArrayInputStream(new byte[]{9});
        when(repository.findById("file-1")).thenReturn(Optional.of(f));
        when(storage.download("files/k.mp4")).thenReturn(stream);

        Optional<InputStream> result = service.downloadById("file-1");

        assertTrue(result.isPresent());
        assertSame(stream, result.get());
    }

    @Test
    void deleteById_removesBinaryThenMetadata() {
        StoredFile f = StoredFile.builder().id("file-1").storageKey("files/k.mp4").build();
        when(repository.findById("file-1")).thenReturn(Optional.of(f));

        service.deleteById("file-1");

        var order = inOrder(storage, repository);
        order.verify(storage).delete("files/k.mp4");
        order.verify(repository).deleteById("file-1");
    }

    @Test
    void deleteById_notFound_noOp() {
        when(repository.findById("missing")).thenReturn(Optional.empty());

        service.deleteById("missing");

        verifyNoInteractions(storage);
        verify(repository, never()).deleteById(anyString());
    }
}
