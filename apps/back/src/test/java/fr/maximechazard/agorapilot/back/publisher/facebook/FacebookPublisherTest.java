package fr.maximechazard.agorapilot.back.publisher.facebook;

import fr.maximechazard.agorapilot.back.media.Media;
import fr.maximechazard.agorapilot.back.media.MediaFileType;
import fr.maximechazard.agorapilot.back.media.publishing.PreparedImages;
import fr.maximechazard.agorapilot.back.media.publishing.PublishableImage;
import fr.maximechazard.agorapilot.back.media.publishing.PublishableImageFactory;
import fr.maximechazard.agorapilot.back.publication.*;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationDeliveryRepository;
import fr.maximechazard.agorapilot.back.publisher.suspension.ChannelSuspensionService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.http.HttpStatus;
import org.springframework.web.client.HttpClientErrorException;
import org.springframework.web.client.HttpServerErrorException;
import tools.jackson.databind.ObjectMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
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
    @Mock
    private ChannelSuspensionService suspensionService;

    private FacebookPublisher publisher;
    private PublicationOccurrence occurrence;
    private PublicationDelivery delivery;

    @BeforeEach
    void setUp() {
        publisher = new FacebookPublisher(tokenService, facebookClient, deliveryRepository, imageFactory, new ObjectMapper(),
                suspensionService);

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

    /**
     * Cas réel du 26/09/2026 : le calendrier affichait le corps brut de la
     * réponse, un JSON de 1 000 caractères, au lieu de la consigne de Facebook.
     */
    @Test
    void records_only_the_message_of_a_graph_api_refusal() {
        String body = """
                {"error":{"message":"Confirmez votre identit\\u00e9 avant de pouvoir publier au nom de cette Page.",\
                "type":"OAuthException","code":368,"error_data":{"sentry_block_data":"AetGWG5rRV9R","is_silent":false},\
                "error_subcode":4854002,"error_user_msg":"","fbtrace_id":"AXp8mIKChr4chy31Wrhy0Qb"}}""";
        failWith(HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", null,
                body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8));

        publisher.publish(occurrence);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getErrorMessage()).isEqualTo(
                "Confirmez votre identité avant de pouvoir publier au nom de cette Page."
                        + " (Facebook, code 368, sous-code 4854002)");
    }

    /** Un compte bloqué fait échouer toutes les diffusions suivantes : le canal est coupé au premier refus. */
    @Test
    void suspends_the_channel_when_facebook_blocks_the_page() {
        failWith(graphRefusal(368, "Confirmez votre identité avant de pouvoir publier au nom de cette Page."));

        publisher.publish(occurrence);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        verify(suspensionService).suspend(DeliveryChannel.FACEBOOK,
                "Confirmez votre identité avant de pouvoir publier au nom de cette Page. (Facebook, code 368)");
    }

    @Test
    void suspends_the_channel_when_the_token_is_invalid() {
        failWith(graphRefusal(190, "Error validating access token: Session has expired."));

        publisher.publish(occurrence);

        verify(suspensionService).suspend(DeliveryChannel.FACEBOOK,
                "Error validating access token: Session has expired. (Facebook, code 190)");
    }

    /** Un refus propre à ce post (texte trop long, image refusée…) ne dit rien des suivants. */
    @Test
    void does_not_suspend_the_channel_for_a_refusal_of_this_post_only() {
        failWith(graphRefusal(100, "Invalid parameter"));

        publisher.publish(occurrence);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        verify(suspensionService, never()).suspend(any(), any());
    }

    @Test
    void does_not_suspend_the_channel_on_a_network_failure() {
        failWith(new IllegalStateException("Connection reset"));

        publisher.publish(occurrence);

        verifyNoInteractions(suspensionService);
    }

    /** Une page d'erreur d'un proxy n'a pas la forme Graph : mieux vaut le message brut que rien. */
    @Test
    void keeps_the_raw_message_when_the_error_body_is_not_a_graph_error() {
        HttpServerErrorException error = HttpServerErrorException.create(HttpStatus.BAD_GATEWAY, "Bad Gateway", null,
                "<html>502</html>".getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
        failWith(error);

        publisher.publish(occurrence);

        assertThat(delivery.getStatus()).isEqualTo(DeliveryStatus.FAILED);
        assertThat(delivery.getErrorMessage()).isEqualTo(error.getMessage());
    }

    private static HttpClientErrorException graphRefusal(int code, String message) {
        String body = "{\"error\":{\"message\":\"" + message + "\",\"type\":\"OAuthException\",\"code\":" + code + "}}";
        return HttpClientErrorException.create(HttpStatus.BAD_REQUEST, "Bad Request", null,
                body.getBytes(StandardCharsets.UTF_8), StandardCharsets.UTF_8);
    }

    private void failWith(RuntimeException error) {
        when(tokenService.getCurrentPageToken()).thenReturn("page-token");
        when(imageFactory.prepare(any(), anyInt())).thenReturn(new PreparedImages(List.of(), List.of()));
        when(facebookClient.publish(any(), any(), any())).thenThrow(error);
    }

    private Media media() {
        Media media = new Media("2026/08/a.png", "a.png", MediaFileType.PNG, 100L, "cafe");
        media.setId(5L);
        return media;
    }
}
