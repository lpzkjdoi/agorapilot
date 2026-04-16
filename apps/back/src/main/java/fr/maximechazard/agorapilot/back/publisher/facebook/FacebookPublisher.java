package fr.maximechazard.agorapilot.back.publisher.facebook;

import fr.maximechazard.agorapilot.back.publication.*;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationDeliveryRepository;
import fr.maximechazard.agorapilot.back.publisher.Publisher;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class FacebookPublisher implements Publisher {
    private final FacebookTokenService facebookTokenService;
    private final FacebookClient facebookClient;
    private final PublicationDeliveryRepository publicationDeliveryRepository;

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

            FacebookPostResponse response = facebookClient.publish(publication, token);

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
}
