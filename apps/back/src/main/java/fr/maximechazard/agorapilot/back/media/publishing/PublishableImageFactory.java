package fr.maximechazard.agorapilot.back.media.publishing;

import fr.maximechazard.agorapilot.back.media.Media;
import fr.maximechazard.agorapilot.back.media.MediaFileType;
import fr.maximechazard.agorapilot.back.media.storage.MediaStorageException;
import fr.maximechazard.agorapilot.back.media.storage.MediaStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;

/**
 * Charge les visuels d'une publication et les met dans un état publiable.
 * <p>
 * Deux traductions y sont faites, parce qu'aucun réseau social ne prend le
 * contenu de la médiathèque tel quel :
 * <ul>
 *   <li>les PDF sont écartés — ils ne sont pas publiables comme photo ;</li>
 *   <li>les WebP sont transcodés en JPEG — l'API Photos de Facebook ne les
 *       accepte pas, et le JDK ne sait les lire que grâce à TwelveMonkeys.</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class PublishableImageFactory {

    private final MediaStorageService storage;

    /**
     * @param medias les médias rattachés, dans l'ordre
     * @param max    nombre maximal d'images accepté par le canal
     */
    public PreparedImages prepare(List<Media> medias, int max) {
        List<PublishableImage> images = new ArrayList<>();
        List<String> skipped = new ArrayList<>();

        for (Media media : medias) {
            if (!media.getFileType().isRaster()) {
                skipped.add(label(media) + " (un " + extension(media) + " n'est pas publiable comme photo)");
                continue;
            }

            if (images.size() >= max) {
                skipped.add(label(media) + " (au-delà de " + max + " photos par publication)");
                continue;
            }

            try {
                images.add(load(media));
            } catch (IOException | MediaStorageException e) {
                log.warn("Média {} : chargement impossible pour la diffusion", media.getId(), e);
                skipped.add(label(media) + " (fichier illisible)");
            }
        }

        return new PreparedImages(List.copyOf(images), List.copyOf(skipped));
    }

    private PublishableImage load(Media media) throws IOException {
        byte[] content;
        try (InputStream in = storage.load(media.getStorageKey()).getInputStream()) {
            content = in.readAllBytes();
        }

        if (media.getFileType() == MediaFileType.WEBP) {
            return new PublishableImage(
                    replaceExtension(media.getOriginalFilename(), "jpg"),
                    MediaFileType.JPEG.getContentType(),
                    media.getAltText(),
                    toJpeg(content));
        }

        return new PublishableImage(
                media.getOriginalFilename(),
                media.getFileType().getContentType(),
                media.getAltText(),
                content);
    }

    /**
     * Transcode en JPEG. La transparence éventuelle est aplatie sur du blanc :
     * le JPEG n'a pas de canal alpha, et sans cet aplatissement les zones
     * transparentes ressortent en noir.
     */
    private byte[] toJpeg(byte[] content) throws IOException {
        BufferedImage source = ImageIO.read(new ByteArrayInputStream(content));

        if (source == null) {
            throw new IOException("Aucun décodeur disponible pour ce contenu");
        }

        BufferedImage opaque = new BufferedImage(
                source.getWidth(), source.getHeight(), BufferedImage.TYPE_INT_RGB);

        Graphics2D graphics = opaque.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, source.getWidth(), source.getHeight());
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }

        ByteArrayOutputStream out = new ByteArrayOutputStream();
        if (!ImageIO.write(opaque, "jpg", out)) {
            throw new IOException("Aucun encodeur JPEG disponible");
        }

        return out.toByteArray();
    }

    private String label(Media media) {
        String title = media.getTitle();
        return title != null && !title.isBlank() ? title.trim() : media.getOriginalFilename();
    }

    private String extension(Media media) {
        return media.getFileType().getExtension().toUpperCase();
    }

    private String replaceExtension(String filename, String extension) {
        int dot = filename.lastIndexOf('.');
        return (dot > 0 ? filename.substring(0, dot) : filename) + "." + extension;
    }
}
