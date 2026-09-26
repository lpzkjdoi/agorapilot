package fr.maximechazard.agorapilot.back.media;

import java.util.Arrays;
import java.util.Optional;

/**
 * Formats de fichiers acceptés par la médiathèque.
 * <p>
 * La liste est délibérément fermée : elle sert à la fois de liste blanche à
 * l'upload, de source de l'extension du fichier stocké et du {@code Content-Type}
 * renvoyé à la lecture. Le SVG en est volontairement absent — servi en
 * {@code inline}, il exécuterait son JavaScript dans l'origine de l'application.
 */
public enum MediaFileType {
    JPEG("image/jpeg", "jpg", true),
    PNG("image/png", "png", true),
    WEBP("image/webp", "webp", true),
    PDF("application/pdf", "pdf", false);

    private final String contentType;
    private final String extension;

    /**
     * Vrai pour les images matricielles : seules celles-ci ont des dimensions
     * lisibles et sont diffusables sur un réseau social.
     */
    private final boolean raster;

    MediaFileType(String contentType, String extension, boolean raster) {
        this.contentType = contentType;
        this.extension = extension;
        this.raster = raster;
    }

    public String getContentType() {
        return contentType;
    }

    public String getExtension() {
        return extension;
    }

    public boolean isRaster() {
        return raster;
    }

    public static Optional<MediaFileType> fromContentType(String contentType) {
        return Arrays.stream(values())
                .filter(type -> type.contentType.equalsIgnoreCase(contentType))
                .findFirst();
    }
}
