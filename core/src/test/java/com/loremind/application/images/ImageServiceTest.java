package com.loremind.application.images;

import com.loremind.domain.images.Image;
import com.loremind.domain.images.ports.ImageRepository;
import com.loremind.domain.images.ports.ImageStorage;
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
 * Test unitaire pour ImageService.
 * Couvre : validation upload (filename/MIME/size), happy path upload, compensation
 * en cas d'échec DB après upload MinIO réussi, download et delete.
 */
@ExtendWith(MockitoExtension.class)
public class ImageServiceTest {

    @Mock private ImageRepository imageRepository;
    @Mock private ImageStorage imageStorage;

    @InjectMocks private ImageService imageService;

    private InputStream data;

    @BeforeEach
    void setUp() {
        data = new ByteArrayInputStream(new byte[]{1, 2, 3});
    }

    @Test
    void testUpload_HappyPath_PersistsMetadata() {
        when(imageStorage.upload(eq("portrait.jpg"), eq("image/jpeg"), any(), eq(1024L)))
                .thenReturn("images/abc.jpg");
        when(imageRepository.save(any(Image.class))).thenAnswer(inv -> {
            Image i = inv.getArgument(0);
            i.setId("img-1");
            return i;
        });

        Image result = imageService.upload("portrait.jpg", "image/jpeg", data, 1024L);

        assertEquals("img-1", result.getId());
        assertEquals("images/abc.jpg", result.getStorageKey());
        assertNotNull(result.getUploadedAt());

        ArgumentCaptor<Image> captor = ArgumentCaptor.forClass(Image.class);
        verify(imageRepository).save(captor.capture());
        Image saved = captor.getValue();
        assertEquals("portrait.jpg", saved.getFilename());
        assertEquals("image/jpeg", saved.getContentType());
        assertEquals(1024L, saved.getSizeBytes());
    }

    @Test
    void testUpload_NormalizesContentTypeCase() {
        when(imageStorage.upload(anyString(), anyString(), any(), anyLong())).thenReturn("k");
        when(imageRepository.save(any(Image.class))).thenAnswer(inv -> inv.getArgument(0));

        // MIME en majuscules doit etre accepte (normalisation en lowercase lors de la validation)
        assertDoesNotThrow(() -> imageService.upload("a.png", "IMAGE/PNG", data, 100L));
    }

    @Test
    void testUpload_DbFailure_CompensatesByDeletingBinary() {
        when(imageStorage.upload(anyString(), anyString(), any(), anyLong()))
                .thenReturn("images/orphan.jpg");
        when(imageRepository.save(any(Image.class))).thenThrow(new RuntimeException("DB down"));

        RuntimeException ex = assertThrows(RuntimeException.class,
                () -> imageService.upload("a.jpg", "image/jpeg", data, 500L));
        assertEquals("DB down", ex.getMessage());
        // Compensation : suppression du binaire orphelin
        verify(imageStorage).delete("images/orphan.jpg");
    }

    @Test
    void testUpload_BlankFilename_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> imageService.upload("  ", "image/jpeg", data, 100L));
        verifyNoInteractions(imageStorage);
        verifyNoInteractions(imageRepository);
    }

    @Test
    void testUpload_NullFilename_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> imageService.upload(null, "image/jpeg", data, 100L));
    }

    @Test
    void testUpload_UnsupportedMime_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> imageService.upload("a.pdf", "application/pdf", data, 100L));
        verifyNoInteractions(imageStorage);
    }

    @Test
    void testUpload_NullMime_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> imageService.upload("a.jpg", null, data, 100L));
    }

    @Test
    void testUpload_ZeroSize_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> imageService.upload("a.jpg", "image/jpeg", data, 0L));
    }

    @Test
    void testUpload_NegativeSize_Throws() {
        assertThrows(IllegalArgumentException.class,
                () -> imageService.upload("a.jpg", "image/jpeg", data, -1L));
    }

    @Test
    void testUpload_TooLarge_Throws() {
        long tooBig = 10L * 1024 * 1024 + 1;
        assertThrows(IllegalArgumentException.class,
                () -> imageService.upload("a.jpg", "image/jpeg", data, tooBig));
        verifyNoInteractions(imageStorage);
    }

    @Test
    void testUpload_ExactMaxSize_Accepted() {
        long max = 10L * 1024 * 1024;
        when(imageStorage.upload(anyString(), anyString(), any(), eq(max))).thenReturn("k");
        when(imageRepository.save(any(Image.class))).thenAnswer(inv -> inv.getArgument(0));

        assertDoesNotThrow(() -> imageService.upload("a.jpg", "image/jpeg", data, max));
    }

    @Test
    void testGetById_DelegatesToRepository() {
        Image img = Image.builder().id("img-1").build();
        when(imageRepository.findById("img-1")).thenReturn(Optional.of(img));

        assertEquals(Optional.of(img), imageService.getById("img-1"));
    }

    @Test
    void testDownloadById_FoundReturnsStream() {
        Image img = Image.builder().id("img-1").storageKey("images/k.jpg").build();
        InputStream stream = new ByteArrayInputStream(new byte[]{9});
        when(imageRepository.findById("img-1")).thenReturn(Optional.of(img));
        when(imageStorage.download("images/k.jpg")).thenReturn(stream);

        Optional<InputStream> result = imageService.downloadById("img-1");

        assertTrue(result.isPresent());
        assertSame(stream, result.get());
    }

    @Test
    void testDownloadById_NotFoundReturnsEmpty() {
        when(imageRepository.findById("missing")).thenReturn(Optional.empty());

        assertTrue(imageService.downloadById("missing").isEmpty());
        verifyNoInteractions(imageStorage);
    }

    @Test
    void testDeleteById_RemovesBinaryThenMetadata() {
        Image img = Image.builder().id("img-1").storageKey("images/k.jpg").build();
        when(imageRepository.findById("img-1")).thenReturn(Optional.of(img));

        imageService.deleteById("img-1");

        // Ordre important : binaire d'abord, metadata ensuite.
        var order = inOrder(imageStorage, imageRepository);
        order.verify(imageStorage).delete("images/k.jpg");
        order.verify(imageRepository).deleteById("img-1");
    }

    @Test
    void testDeleteById_NotFound_NoOp() {
        when(imageRepository.findById("missing")).thenReturn(Optional.empty());

        imageService.deleteById("missing");

        verifyNoInteractions(imageStorage);
        verify(imageRepository, never()).deleteById(anyString());
    }
}
