package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.media.Media;
import fr.maximechazard.agorapilot.back.media.MediaFileType;
import fr.maximechazard.agorapilot.back.media.MediaMapper;
import fr.maximechazard.agorapilot.back.media.MediaRepository;
import fr.maximechazard.agorapilot.back.media.exceptions.MediaNotFoundException;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDTO;
import fr.maximechazard.agorapilot.back.publication.exceptions.DuplicateMediaException;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotFoundException;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * Couvre le rattachement ordonné des visuels à une publication.
 */
@ExtendWith(MockitoExtension.class)
class PublicationServiceMediasTest {

    private static final long PUBLICATION_ID = 7L;

    @Mock
    private PublicationRepository publicationRepository;
    @Mock
    private MediaRepository mediaRepository;

    private PublicationService service;
    private Publication publication;

    @BeforeEach
    void setUp() {
        service = new PublicationService(publicationRepository, mediaRepository, new PublicationMapper(new MediaMapper()));
        publication = new Publication("Marché de producteurs", PublicationStatus.DRAFT);
        publication.setId(PUBLICATION_ID);
    }

    @Test
    void attaches_the_medias_in_the_order_they_were_sent() {
        givenPublication();
        givenSaveEchoesBack();
        givenMedias(3L, 1L, 7L);

        PublicationDTO dto = service.setMedias(PUBLICATION_ID, List.of(3L, 1L, 7L));

        // L'ordre fait foi : le premier média sert de vignette et de première photo.
        assertThat(dto.medias()).extracting(m -> m.id()).containsExactly(3L, 1L, 7L);
        assertThat(publication.getMedias()).extracting(PublicationMedia::getPosition).containsExactly(0, 1, 2);
    }

    @Test
    void replaces_the_previous_selection_rather_than_adding_to_it() {
        givenPublication();
        givenSaveEchoesBack();
        givenMedias(1L, 2L);
        service.setMedias(PUBLICATION_ID, List.of(1L, 2L));

        givenMedias(2L);
        PublicationDTO dto = service.setMedias(PUBLICATION_ID, List.of(2L));

        assertThat(dto.medias()).extracting(m -> m.id()).containsExactly(2L);
    }

    @Test
    void detaches_everything_on_an_empty_list() {
        givenPublication();
        givenSaveEchoesBack();
        givenMedias(1L);
        service.setMedias(PUBLICATION_ID, List.of(1L));

        assertThat(service.setMedias(PUBLICATION_ID, List.of()).medias()).isEmpty();
    }

    @Test
    void treats_a_missing_list_as_an_empty_one() {
        givenPublication();
        givenSaveEchoesBack();

        assertThat(service.setMedias(PUBLICATION_ID, null).medias()).isEmpty();
    }

    @Test
    void refuses_a_list_holding_the_same_media_twice() {
        givenPublication();

        assertThatThrownBy(() -> service.setMedias(PUBLICATION_ID, List.of(3L, 3L)))
                .isInstanceOf(DuplicateMediaException.class);

        verify(publicationRepository, never()).save(any());
    }

    @Test
    void fails_on_an_unknown_media_without_saving_a_partial_selection() {
        givenPublication();
        when(mediaRepository.findById(1L)).thenReturn(Optional.of(media(1L)));
        when(mediaRepository.findById(404L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setMedias(PUBLICATION_ID, List.of(1L, 404L)))
                .isInstanceOf(MediaNotFoundException.class);

        verify(publicationRepository, never()).save(any());
    }

    @Test
    void fails_on_an_unknown_publication() {
        when(publicationRepository.findById(PUBLICATION_ID)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.setMedias(PUBLICATION_ID, List.of(1L)))
                .isInstanceOf(PublicationNotFoundException.class);
    }

    @Test
    void keeps_the_media_itself_untouched_when_detaching() {
        // La médiathèque est une bibliothèque partagée : détacher n'efface pas.
        givenPublication();
        givenSaveEchoesBack();
        givenMedias(1L);
        service.setMedias(PUBLICATION_ID, List.of(1L));

        service.setMedias(PUBLICATION_ID, List.of());

        verify(mediaRepository, never()).delete(any());
        verify(mediaRepository, never()).deleteById(any());
    }

    private void givenPublication() {
        when(publicationRepository.findById(PUBLICATION_ID)).thenReturn(Optional.of(publication));
    }

    /** À n'appeler que dans les cas qui vont réellement jusqu'à la sauvegarde. */
    private void givenSaveEchoesBack() {
        when(publicationRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
    }

    private void givenMedias(Long... ids) {
        for (Long id : ids) {
            when(mediaRepository.findById(id)).thenReturn(Optional.of(media(id)));
        }
    }

    private Media media(Long id) {
        Media media = new Media("2026/08/" + id + ".png", id + ".png", MediaFileType.PNG, 100L, "cafe" + id);
        media.setId(id);
        return media;
    }
}
