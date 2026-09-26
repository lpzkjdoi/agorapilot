package fr.maximechazard.agorapilot.back.media;

import org.junit.jupiter.api.Test;

import java.nio.charset.StandardCharsets;
import java.util.Arrays;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * La détection est le seul rempart entre un fichier déposé et la médiathèque :
 * l'extension et le {@code Content-Type} venant du client sont ignorés.
 */
class MediaTypeDetectorTest {

    private final MediaTypeDetector detector = new MediaTypeDetector();

    @Test
    void recognizes_a_jpeg() {
        assertThat(detector.detect(header((byte) 0xFF, (byte) 0xD8, (byte) 0xFF, (byte) 0xE0)))
                .contains(MediaFileType.JPEG);
    }

    @Test
    void recognizes_a_png() {
        assertThat(detector.detect(header((byte) 0x89, 'P', 'N', 'G', (byte) 0x0D, (byte) 0x0A, (byte) 0x1A, (byte) 0x0A)))
                .contains(MediaFileType.PNG);
    }

    @Test
    void recognizes_a_webp_by_its_riff_container_and_its_form_marker() {
        byte[] webp = header('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P');
        assertThat(detector.detect(webp)).contains(MediaFileType.WEBP);
    }

    @Test
    void rejects_a_riff_container_that_is_not_a_webp() {
        // Un WAV commence lui aussi par RIFF : le marqueur de forme est ce qui
        // les distingue.
        byte[] wav = header('R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'A', 'V', 'E');
        assertThat(detector.detect(wav)).isEmpty();
    }

    @Test
    void recognizes_a_pdf() {
        assertThat(detector.detect(header('%', 'P', 'D', 'F', '-', '1', '.', '7'))).contains(MediaFileType.PDF);
    }

    @Test
    void rejects_an_svg_even_though_it_is_an_image() {
        // Exclu délibérément : servi en `inline`, un SVG exécute son JavaScript
        // dans l'origine de l'application.
        assertThat(detector.detect("<svg xmlns=".getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    @Test
    void rejects_content_that_matches_nothing() {
        assertThat(detector.detect("Bonjour tout le monde".getBytes(StandardCharsets.UTF_8))).isEmpty();
    }

    @Test
    void rejects_an_empty_or_missing_header() {
        assertThat(detector.detect(new byte[0])).isEmpty();
        assertThat(detector.detect(null)).isEmpty();
    }

    /** Complète la signature jusqu'à la taille d'en-tête lue par le service. */
    private byte[] header(int... bytes) {
        byte[] header = new byte[MediaTypeDetector.HEADER_LENGTH];
        Arrays.fill(header, (byte) 0);
        for (int i = 0; i < bytes.length && i < header.length; i++) {
            header[i] = (byte) bytes[i];
        }
        return header;
    }
}
