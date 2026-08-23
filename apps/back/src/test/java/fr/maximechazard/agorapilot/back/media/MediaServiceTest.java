package fr.maximechazard.agorapilot.back.media;

import fr.maximechazard.agorapilot.back.media.dtos.MediaDTO;
import fr.maximechazard.agorapilot.back.media.exceptions.MediaNotFoundException;
import fr.maximechazard.agorapilot.back.media.exceptions.UnsupportedMediaFileTypeException;
import fr.maximechazard.agorapilot.back.media.requests.UpdateMediaRequest;
import fr.maximechazard.agorapilot.back.media.storage.MediaStorageService;
import fr.maximechazard.agorapilot.back.media.storage.StoredFile;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.core.io.ByteArrayResource;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MediaServiceTest {

    private static final byte[] PNG_1x1 = png1x1();

    @Mock
    private MediaRepository mediaRepository;
    @Mock
    private MediaStorageService storage;

    private MediaService service;

    @BeforeEach
    void setUp() {
        service = new MediaService(
                mediaRepository,
                storage,
                new MediaTypeDetector(),
                new MediaMapper(),
                new MediaProperties("/tmp/medias", List.of("image/jpeg", "image/png", "application/pdf")));
    }

    // ------------------------------- Dépôt ---------------------------------

    @Test
    void stores_the_file_and_saves_the_metadata_it_actually_measured() {
        when(storage.store(any(), any())).thenReturn(new StoredFile("2026/08/abc.png", 95L, "cafe"));
        when(storage.load("2026/08/abc.png")).thenReturn(new ByteArrayResource(PNG_1x1));
        when(mediaRepository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 5L));

        MediaDTO dto = service.upload(
                new MockMultipartFile("file", "Affiche été.png", "image/png", PNG_1x1),
                " Fête de la musique ", "Affiche de la fête");

        ArgumentCaptor<Media> saved = ArgumentCaptor.forClass(Media.class);
        verify(mediaRepository).save(saved.capture());

        assertThat(saved.getValue().getStorageKey()).isEqualTo("2026/08/abc.png");
        assertThat(saved.getValue().getFileType()).isEqualTo(MediaFileType.PNG);
        // La taille et l'empreinte viennent du stockage, pas de ce qu'annonce le client.
        assertThat(saved.getValue().getSizeBytes()).isEqualTo(95L);
        assertThat(saved.getValue().getChecksum()).isEqualTo("cafe");
        assertThat(saved.getValue().getTitle()).isEqualTo("Fête de la musique");
        assertThat(saved.getValue().getArchived()).isFalse();

        assertThat(dto.id()).isEqualTo(5L);
        assertThat(dto.contentType()).isEqualTo("image/png");
    }

    @Test
    void reads_the_dimensions_of_a_raster_image() {
        when(storage.store(any(), any())).thenReturn(new StoredFile("2026/08/abc.png", 95L, "cafe"));
        when(storage.load("2026/08/abc.png")).thenReturn(new ByteArrayResource(PNG_1x1));
        when(mediaRepository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 5L));

        MediaDTO dto = service.upload(new MockMultipartFile("file", "a.png", "image/png", PNG_1x1), null, null);

        assertThat(dto.width()).isEqualTo(1);
        assertThat(dto.height()).isEqualTo(1);
    }

    @Test
    void leaves_the_dimensions_empty_for_a_pdf_without_trying_to_read_it() {
        byte[] pdf = "%PDF-1.7\n%aaa\n".getBytes(StandardCharsets.UTF_8);
        when(storage.store(any(), any())).thenReturn(new StoredFile("2026/08/abc.pdf", 14L, "beef"));
        when(mediaRepository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 6L));

        MediaDTO dto = service.upload(new MockMultipartFile("file", "affiche.pdf", "application/pdf", pdf), null, null);

        assertThat(dto.width()).isNull();
        assertThat(dto.height()).isNull();
        verify(storage, never()).load(any());
    }

    @Test
    void rejects_a_file_whose_content_does_not_match_its_extension() {
        // Le cas d'attaque de base : n'importe quoi renommé en .jpg et annoncé
        // comme une image par le navigateur.
        MockMultipartFile disguised = new MockMultipartFile(
                "file", "photo.jpg", "image/jpeg", "#!/bin/sh\nrm -rf /\n".getBytes(StandardCharsets.UTF_8));

        assertThatThrownBy(() -> service.upload(disguised, null, null))
                .isInstanceOf(UnsupportedMediaFileTypeException.class)
                .hasMessageContaining("Format non reconnu");

        verifyNoInteractions(storage);
        verifyNoInteractions(mediaRepository);
    }

    @Test
    void rejects_a_recognized_format_that_is_not_in_the_allow_list() {
        // Le WebP est absent de la liste blanche de ce test : reconnu, mais refusé.
        byte[] webp = new byte[]{'R', 'I', 'F', 'F', 0, 0, 0, 0, 'W', 'E', 'B', 'P'};

        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "a.webp", "image/webp", webp), null, null))
                .isInstanceOf(UnsupportedMediaFileTypeException.class)
                .hasMessageContaining("non accepté");

        verifyNoInteractions(storage);
    }

    @Test
    void rejects_an_empty_file() {
        assertThatThrownBy(() -> service.upload(new MockMultipartFile("file", "vide.png", "image/png", new byte[0]), null, null))
                .isInstanceOf(UnsupportedMediaFileTypeException.class)
                .hasMessageContaining("vide");
    }

    @Test
    void falls_back_to_a_generated_name_when_the_client_sends_none() {
        when(storage.store(any(), any())).thenReturn(new StoredFile("2026/08/abc.png", 95L, "cafe"));
        when(storage.load(any())).thenReturn(new ByteArrayResource(PNG_1x1));
        when(mediaRepository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 5L));

        MediaDTO dto = service.upload(new MockMultipartFile("file", "", "image/png", PNG_1x1), null, null);

        assertThat(dto.originalFilename()).isEqualTo("media.png");
    }

    @Test
    void strips_path_separators_and_quotes_from_the_client_filename() {
        when(storage.store(any(), any())).thenReturn(new StoredFile("2026/08/abc.png", 95L, "cafe"));
        when(storage.load(any())).thenReturn(new ByteArrayResource(PNG_1x1));
        when(mediaRepository.save(any())).thenAnswer(invocation -> withId(invocation.getArgument(0), 5L));

        MediaDTO dto = service.upload(
                new MockMultipartFile("file", "../../etc/pas\"swd.png", "image/png", PNG_1x1), null, null);

        assertThat(dto.originalFilename()).doesNotContain("..", "/", "\"");
    }

    // ------------------------------ Lecture --------------------------------

    @Test
    void lists_only_the_unarchived_medias_when_asked_to() {
        when(mediaRepository.findAllByArchivedOrderByCreatedAtDesc(false)).thenReturn(List.of(media()));

        assertThat(service.getAll(false)).hasSize(1);
        verify(mediaRepository, never()).findAllByOrderByCreatedAtDesc();
    }

    @Test
    void lists_everything_when_no_filter_is_given() {
        when(mediaRepository.findAllByOrderByCreatedAtDesc()).thenReturn(List.of(media()));

        assertThat(service.getAll(null)).hasSize(1);
    }

    @Test
    void fails_with_a_404_carrying_exception_on_an_unknown_media() {
        when(mediaRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.get(404L)).isInstanceOf(MediaNotFoundException.class);
    }

    // ------------------------- Mise à jour et suppression ------------------

    @Test
    void updates_only_the_fields_that_were_sent() {
        Media existing = media();
        existing.setTitle("Titre initial");
        existing.setAltText("Description initiale");
        when(mediaRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(mediaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateMediaRequest request = new UpdateMediaRequest();
        ReflectionTestUtils.setField(request, "archived", true);

        MediaDTO dto = service.update(1L, request);

        assertThat(dto.archived()).isTrue();
        assertThat(dto.title()).isEqualTo("Titre initial");
        assertThat(dto.altText()).isEqualTo("Description initiale");
    }

    @Test
    void clears_a_field_when_an_empty_string_is_sent() {
        Media existing = media();
        existing.setTitle("Titre initial");
        when(mediaRepository.findById(1L)).thenReturn(Optional.of(existing));
        when(mediaRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));

        UpdateMediaRequest request = new UpdateMediaRequest();
        ReflectionTestUtils.setField(request, "title", "   ");

        assertThat(service.update(1L, request).title()).isNull();
    }

    @Test
    void deletes_the_row_and_then_the_file() {
        Media existing = media();
        when(mediaRepository.findById(1L)).thenReturn(Optional.of(existing));

        service.delete(1L);

        verify(mediaRepository).delete(existing);
        // Hors transaction, la synchronisation se joue immédiatement.
        verify(storage).delete("2026/08/abc.png");
    }

    // ------------------------------ Fixtures -------------------------------

    private Media media() {
        Media media = new Media("2026/08/abc.png", "a.png", MediaFileType.PNG, 95L, "cafe");
        media.setId(1L);
        return media;
    }

    private Media withId(Media media, Long id) {
        media.setId(id);
        return media;
    }

    /** Le plus petit PNG valide : de quoi exercer réellement ImageIO. */
    private static byte[] png1x1() {
        try {
            java.awt.image.BufferedImage image =
                    new java.awt.image.BufferedImage(1, 1, java.awt.image.BufferedImage.TYPE_INT_RGB);
            ByteArrayOutputStream out = new ByteArrayOutputStream();
            javax.imageio.ImageIO.write(image, "png", out);
            return out.toByteArray();
        } catch (IOException e) {
            throw new IllegalStateException(e);
        }
    }
}
