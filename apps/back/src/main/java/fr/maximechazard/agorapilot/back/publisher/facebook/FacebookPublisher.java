package fr.maximechazard.agorapilot.back.publisher.facebook;

import fr.maximechazard.agorapilot.back.media.Media;
import fr.maximechazard.agorapilot.back.media.publishing.PreparedImages;
import fr.maximechazard.agorapilot.back.media.publishing.PublishableImageFactory;
import fr.maximechazard.agorapilot.back.publication.*;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationDeliveryRepository;
import fr.maximechazard.agorapilot.back.publisher.Publisher;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FacebookPublisher implements Publisher {
    private final FacebookTokenService facebookTokenService;
    private final FacebookClient facebookClient;
    private final PublicationDeliveryRepository publicationDeliveryRepository;
    private final PublishableImageFactory publishableImageFactory;

    @Override
    public DeliveryChannel supports() {
        return DeliveryChannel.FACEBOOK;
    }

    @Override
    public void publish(PublicationOccurrence occurrence) {
        PublicationDelivery delivery = publicationDeliveryRepository
                .findByOccurrenceAndChannel(occurrence, DeliveryChannel.FACEBOOK)
                .orElseThrow(() -> new IllegalStateException("Facebook delivery not found for occurrence " + occurrence.getId()));

        try {
            String token = facebookTokenService.getCurrentPageToken();

            Publication publication = occurrence.getPublication();
            PreparedImages prepared = publishableImageFactory.prepare(
                    attachedMedias(publication), FacebookClient.MAX_PHOTOS_PER_POST);

            if (!prepared.skipped().isEmpty()) {
                // Non bloquant : le texte et les autres visuels partent quand même.
                // Tracé pour que l'écart entre ce qui était rattaché et ce qui a été
                // diffusé soit explicable après coup.
                log.warn("Publication {} : médias non diffusés sur Facebook — {}",
                        publication.getId(), String.join(", ", prepared.skipped()));
            }

            FacebookPostResponse response = facebookClient.publish(
                    publication.getContent(), prepared.images(), token);

            delivery.setStatus(DeliveryStatus.PUBLISHED);
            delivery.setPublishedAt(LocalDateTime.now());
            delivery.setExternalId(response.id());
            delivery.setErrorMessage(null);

        } catch (Exception e) {
            delivery.setErrorMessage(e.getMessage());
            delivery.setStatus(DeliveryStatus.FAILED);
        }

        publicationDeliveryRepository.save(delivery);
    }

    private List<Media> attachedMedias(Publication publication) {
        if (publication.getMedias() == null) {
            return List.of();
        }

        return publication.getMedias().stream().map(PublicationMedia::getMedia).toList();
    }
}
