package com.loremind.infrastructure.transfer.pdf;

import com.loremind.domain.files.ports.FileStorage;
import com.loremind.domain.images.ports.ImageStorage;
import com.loremind.infrastructure.persistence.entity.ImageJpaEntity;
import com.loremind.infrastructure.persistence.entity.StoredFileJpaEntity;
import com.loremind.infrastructure.persistence.jpa.ImageJpaRepository;
import com.loremind.infrastructure.persistence.jpa.StoredFileJpaRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Base64;

/**
 * Charge les images LoreMind (portraits, illustrations) et les battlemaps (fichiers
 * stockes) et les prepare pour l'inlining PDF : lecture du binaire via le stockage,
 * redimensionnement (max PORTRAIT_MAX / ILLUSTRATION_MAX px), re-encodage JPEG sur fond
 * blanc (aplatit la transparence) et sortie en data-URI. Le decodeur WebP TwelveMonkeys
 * (present au classpath) permet de decoder aussi les WebP. Tout echec de decodage renvoie
 * null : l'image est simplement omise du livret plutot que de casser l'export.
 */
@Component
class PdfImageEncoder {

    private static final Logger log = LoggerFactory.getLogger(PdfImageEncoder.class);

    /** Cotes max (px) avant re-encodage : portraits compacts, battlemaps/illustrations larges. */
    static final int PORTRAIT_MAX = 700;
    static final int ILLUSTRATION_MAX = 1500;

    private final ImageJpaRepository imageRepo;
    private final StoredFileJpaRepository storedFileRepo;
    private final ImageStorage imageStorage;
    private final FileStorage fileStorage;

    PdfImageEncoder(ImageJpaRepository imageRepo, StoredFileJpaRepository storedFileRepo,
                    ImageStorage imageStorage, FileStorage fileStorage) {
        this.imageRepo = imageRepo;
        this.storedFileRepo = storedFileRepo;
        this.imageStorage = imageStorage;
        this.fileStorage = fileStorage;
    }

    /** Image LoreMind re-encodee (data-URI JPEG redimensionne), ou null. */
    PdfImage image(String imageId, int maxDim) {
        if (imageId == null || imageId.isBlank()) return null;
        ImageJpaEntity e;
        try {
            e = imageRepo.findById(Long.parseLong(imageId)).orElse(null);
        } catch (NumberFormatException ex) {
            return null;
        }
        if (e == null) return null;
        return encode(imageStorage.download(e.getStorageKey()), maxDim, imageId);
    }

    /** Battlemap (fichier stocke) re-encodee, seulement si c'est une image (pas une video). */
    PdfImage fileImage(String fileId) {
        if (fileId == null || fileId.isBlank()) return null;
        StoredFileJpaEntity e;
        try {
            e = storedFileRepo.findById(Long.parseLong(fileId)).orElse(null);
        } catch (NumberFormatException ex) {
            return null;
        }
        if (e == null) return null;
        String ct = e.getContentType();
        if (ct == null || !ct.startsWith("image/")) return null; // mp4/webm -> ignore
        return encode(fileStorage.download(e.getStorageKey()), ILLUSTRATION_MAX, fileId);
    }

    /** Lit un flux image, le redimensionne (max maxDim) et le re-encode en data-URI JPEG. */
    private PdfImage encode(InputStream in, int maxDim, String ref) {
        if (in == null) return null;
        try (in) {
            BufferedImage src = ImageIO.read(in);
            if (src == null) {
                log.debug("Image PDF ignoree (format non decode) : {}", ref);
                return null;
            }
            int w = src.getWidth();
            int h = src.getHeight();
            double scale = Math.min(1.0, (double) maxDim / Math.max(w, h));
            int nw = Math.max(1, (int) Math.round(w * scale));
            int nh = Math.max(1, (int) Math.round(h * scale));
            BufferedImage dst = new BufferedImage(nw, nh, BufferedImage.TYPE_INT_RGB);
            Graphics2D g = dst.createGraphics();
            g.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BILINEAR);
            g.setColor(Color.WHITE); // fond blanc : aplatit la transparence (JPEG sans alpha)
            g.fillRect(0, 0, nw, nh);
            g.drawImage(src, 0, 0, nw, nh, null);
            g.dispose();
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            ImageIO.write(dst, "jpeg", out);
            return new PdfImage("data:image/jpeg;base64," + Base64.getEncoder().encodeToString(out.toByteArray()),
                    nw, nh);
        } catch (IOException | RuntimeException ex) {
            log.warn("Image PDF ignoree ({}) : {}", ref, ex.getMessage());
            return null;
        }
    }
}
