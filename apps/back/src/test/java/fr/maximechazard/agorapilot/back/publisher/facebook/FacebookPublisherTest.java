package fr.maximechazard.agorapilot.back.publisher.facebook;

import fr.maximechazard.agorapilot.back.media.Media;
import fr.maximechazard.agorapilot.back.media.MediaFileType;
import fr.maximechazard.agorapilot.back.media.publishing.PreparedImages;
import fr.maximechazard.agorapilot.back.media.publishing.PublishableImage;
import fr.maximechazard.agorapilot.back.media.publishing.PublishableImageFactory;
import fr.maximechazard.agorapilot.back.publication.*;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationDeliveryRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FacebookPublisherTest {

    @Mock
    private FacebookTokenService tokenService;
    @Mock
    private FacebookClient facebookClient;
    @Mock
    private PublicationDeliveryRepository deliveryRepository;
    @Mock
    private PublishableImageFactory imageFactory;

    private FacebookPublisher publisher;
    private PublicationOccurrence occurrence;
    private PublicationDelivery delivery;

    @BeforeEach
    void setUp() {
        publisher = new FacebookPublisher(tokenService, facebookClient, deliveryRepository, imageFactory);

        Publication publication = new Publication("Marché samedi", PublicationStatus.VERIFIED);
        publication.setId(1L);

        occurrence = new PublicationOccurrence();
        occurrence.setId(2L);
        occurrence.setPublication(publication);

        delivery = new PublicationDelivery();
        delivery.setChannel(DeliveryChannel.FACEBOOK);

        when(deliveryRepository.findByOccurrenceAndChannel(occurrence, DeliveryChannel.FACEBOOK))
                .thenReturn(Optional.of(delivery));
    }

    @Test
    void passes_the_prepared_images_and_the_content_to_the_client() {
        Media media = media();
        occurrence.getPublication().getMedias().add(new PublicationMedia(occurrence.getPublication(), media, 0));

        PublishableImage image = new PublishableImage("a.png", "image/png", null,
                "x".getBytes(StandardCharsets.UTF_8));
        when(tokenService.getCurrentPageToken()).thenReturn("page-token");
        when(imageFactory.prepare(List.of(media), FacebookClient.MAX_PHOTOS_PER_POST))
                .thenReturn(new PreparedImages(List.of(image), List.of()));
        when(facebookClient.publish(eq("Marché samedi"), eq(List.of(image)), eq("page-token")))
                .thenReturn(new FacebookPostResponse("123_456"));

        publisher.publish(occurrence);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PUBLISHED);
        assertThat(delivery.getExternalId()).isEqualTo("123_456");
        assertThat(delivery.getErrorMessage()).isNull();
    }

    @Test
    void publishes_without_image_when_nothing_is_attached() {
        when(tokenService.getCurrentPageToken()).thenReturn("page-token");
        when(imageFactory.prepare(List.of(), FacebookClient.MAX_PHOTOS_PER_POST))
                .thenReturn(new PreparedImages(List.of(), List.of()));
        when(facebookClient.publish(any(), any(), any())).thenReturn(new FacebookPostResponse("123_000"));

        publisher.publish(occurrence);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PUBLISHED);
    }

    @Test
    void still_publishes_the_text_when_every_media_was_skipped() {
        // Un PDF seul ne bloque pas la diffusion : le texte part quand même.
        Media pdf = media();
        pdf.setFileType(MediaFileType.PDF);
        occurrence.getPublication().getMedias().add(new PublicationMedia(occurrence.getPublication(), pdf, 0));

        when(tokenService.getCurrentPageToken()).thenReturn("page-token");
        when(imageFactory.prepare(any(), anyInt()))
                .thenReturn(new PreparedImages(List.of(), List.of("programme.pdf (un PDF n'est pas publiable)")));
        when(facebookClient.publish(any(), eq(List.of()), any()))
                .thenReturn(new FacebookPostResponse("123_111"));

        publisher.publish(occurrence);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.PUBLISHED);
    }

    @Test
    void records_the_failure_rather_than_propagating_it() {
        when(tokenService.getCurrentPageToken()).thenReturn("page-token");
        when(imageFactory.prepare(any(), anyInt())).thenReturn(new PreparedImages(List.of(), List.of()));
        when(facebookClient.publish(any(), any(), any())).thenThrow(new IllegalStateException("Graph a refusé"));

        publisher.publish(occurrence);

        ArgumentCaptor<PublicationDelivery> saved = ArgumentCaptor.forClass(PublicationDelivery.class);
        org.mockito.Mockito.verify(deliveryRepository).save(saved.capture());

        assertThat(saved.getValue().getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(saved.getValue().getErrorMessage()).isEqualTo("Graph a refusé");
    }

    private Media media() {
        Media media = new Media("2026/08/a.png", "a.png", MediaFileType.PNG, 100L, "cafe");
        media.setId(5L);
        return media;
    }
}
