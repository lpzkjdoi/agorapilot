package fr.maximechazard.agorapilot.back.media.publishing;

import fr.maximechazard.agorapilot.back.media.Media;
import fr.maximechazard.agorapilot.back.media.MediaFileType;
import fr.maximechazard.agorapilot.back.media.storage.MediaStorageException;
import fr.maximechazard.agorapilot.back.media.storage.MediaStorageService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;

import javax.imageio.ImageIO;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

/**
 * Le point sensible : ce que la médiathèque accepte de stocker n'est pas ce que
 * l'API Photos de Facebook accepte de publier.
 */
@ExtendWith(MockitoExtension.class)
class PublishableImageFactoryTest {

    @Mock
    private MediaStorageService storage;

    private PublishableImageFactory factory;

    @BeforeEach
    void setUp() {
        factory = new PublishableImageFactory(storage);
    }

    @Test
    void loads_a_png_as_is() throws IOException {
        byte[] png = image("png");
        when(storage.load("k/png")).thenReturn(new ByteArrayResource(png));

        PreparedImages prepared = factory.prepare(List.of(media(1L, MediaFileType.PNG, "k/png", "a.png")), 10);

        assertThat(prepared.skipped()).isEmpty();
        assertThat(prepared.images()).singleElement().satisfies(img -> {
            assertThat(img.contentType()).isEqualTo("image/png");
            assertThat(img.filename()).isEqualTo("a.png");
            assertThat(img.content()).isEqualTo(png);
        });
    }

    @Test
    void transcodes_a_webp_to_jpeg_because_the_photos_api_refuses_webp() throws IOException {
        // Le contenu importe peu ici : on vérifie que la sortie est bien un JPEG
        // décodable et que le nom de fichier suit.
        when(storage.load("k/webp")).thenReturn(new ByteArrayResource(image("png")));

        PreparedImages prepared = factory.prepare(
                List.of(media(1L, MediaFileType.WEBP, "k/webp", "affiche.webp")), 10);

        assertThat(prepared.images()).singleElement().satisfies(img -> {
            assertThat(img.contentType()).isEqualTo("image/jpeg");
            assertThat(img.filename()).isEqualTo("affiche.jpg");
            assertThat(ImageIO.read(new ByteArrayInputStream(img.content()))).isNotNull();
        });
    }

    @Test
    void skips_a_pdf_and_says_so() {
        PreparedImages prepared = factory.prepare(
                List.of(media(1L, MediaFileType.PDF, "k/pdf", "programme.pdf")), 10);

        assertThat(prepared.images()).isEmpty();
        assertThat(prepared.skipped()).singleElement()
                                      .satisfies(reason -> assertThat(reason).contains("programme.pdf", "PDF"));
    }

    @Test
    void keeps_the_publishable_images_of_a_mixed_selection() throws IOException {
        when(storage.load("k/png")).thenReturn(new ByteArrayResource(image("png")));

        PreparedImages prepared = factory.prepare(List.of(
                media(1L, MediaFileType.PDF, "k/pdf", "programme.pdf"),
                media(2L, MediaFileType.PNG, "k/png", "a.png")), 10);

        assertThat(prepared.images()).hasSize(1);
        assertThat(prepared.skipped()).hasSize(1);
    }

    @Test
    void caps_the_number_of_photos_and_reports_the_ones_left_out() throws IOException {
        when(storage.load(any())).thenReturn(new ByteArrayResource(image("png")));

        List<Media> medias = List.of(
                media(1L, MediaFileType.PNG, "k/1", "une.png"),
                media(2L, MediaFileType.PNG, "k/2", "deux.png"),
                media(3L, MediaFileType.PNG, "k/3", "trois.png"));

        PreparedImages prepared = factory.prepare(medias, 2);

        assertThat(prepared.images()).hasSize(2);
        assertThat(prepared.skipped()).singleElement()
                                      .satisfies(reason -> assertThat(reason).contains("trois.png", "au-delà de 2"));
    }

    @Test
    void prefers_the_title_over_the_filename_when_reporting_a_skip() {
        Media media = media(1L, MediaFileType.PDF, "k/pdf", "programme.pdf");
        media.setTitle("Programme du marché");

        assertThat(factory.prepare(List.of(media), 10).skipped())
                .singleElement().satisfies(reason -> assertThat(reason).contains("Programme du marché"));
    }

    @Test
    void carries_the_alt_text_through_for_the_caption() throws IOException {
        Media media = media(1L, MediaFileType.PNG, "k/png", "a.png");
        media.setAltText("Affiche de la fête");
        when(storage.load("k/png")).thenReturn(new ByteArrayResource(image("png")));

        assertThat(factory.prepare(List.of(media), 10).images().getFirst().altText())
                .isEqualTo("Affiche de la fête");
    }

    @Test
    void skips_a_media_whose_file_has_vanished_rather_than_failing_the_whole_post() {
        // Le texte et les autres visuels doivent pouvoir partir malgré un fichier
        // manquant : une diffusion partielle vaut mieux qu'aucune.
        when(storage.load("k/png")).thenThrow(new MediaStorageException("disparu"));

        PreparedImages prepared = factory.prepare(
                List.of(media(1L, MediaFileType.PNG, "k/png", "a.png")), 10);

        assertThat(prepared.images()).isEmpty();
        assertThat(prepared.skipped()).singleElement()
                                      .satisfies(reason -> assertThat(reason).contains("illisible"));
    }

    @Test
    void skips_content_that_no_decoder_can_read_when_transcoding_is_required() {
        when(storage.load("k/webp")).thenReturn(
                new ByteArrayResource("pas une image".getBytes(StandardCharsets.UTF_8)));

        PreparedImages prepared = factory.prepare(
                List.of(media(1L, MediaFileType.WEBP, "k/webp", "a.webp")), 10);

        assertThat(prepared.images()).isEmpty();
        assertThat(prepared.skipped()).hasSize(1);
    }

    @Test
    void returns_an_empty_result_for_a_publication_without_media() {
        PreparedImages prepared = factory.prepare(List.of(), 10);

        assertThat(prepared.isEmpty()).isTrue();
        assertThat(prepared.skipped()).isEmpty();
    }

    private Media media(Long id, MediaFileType type, String storageKey, String filename) {
        Media media = new Media(storageKey, filename, type, 100L, "cafe");
        media.setId(id);
        return media;
    }

    private byte[] image(String format) throws IOException {
        BufferedImage image = new BufferedImage(4, 4, BufferedImage.TYPE_INT_RGB);
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        ImageIO.write(image, format, out);
        return out.toByteArray();
    }
}
