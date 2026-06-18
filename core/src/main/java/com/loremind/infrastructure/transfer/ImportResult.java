package com.loremind.infrastructure.transfer;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Resume d'un import : nombre d'entites creees par type, + nombre d'images
 * reuploadees / reutilisees.
 */
public record ImportResult(Map<String, Integer> created, int imagesUploaded, int imagesReused) {

    public static class Builder {
        private final Map<String, Integer> created = new LinkedHashMap<>();
        private int imagesUploaded;
        private int imagesReused;

        public void count(String type, int n) {
            created.merge(type, n, Integer::sum);
        }

        public void imageUploaded() { imagesUploaded++; }

        public void imageReused() { imagesReused++; }

        public ImportResult build() {
            return new ImportResult(created, imagesUploaded, imagesReused);
        }
    }
}
