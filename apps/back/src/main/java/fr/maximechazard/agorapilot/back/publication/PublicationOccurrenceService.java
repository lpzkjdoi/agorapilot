package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotFoundException;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationOccurrenceRepository;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationRepository;
import fr.maximechazard.agorapilot.back.publication.requests.CreatePublicationOccurrenceRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PublicationOccurrenceService {
    private final PublicationRepository publicationRepository;
    private final PublicationOccurrenceRepository publicationOccurrenceRepository;

    public PublicationOccurrence create(CreatePublicationOccurrenceRequest request) {
        Publication publication = publicationRepository.findById(request.getPublicationId())
                .orElseThrow(() -> new PublicationNotFoundException("Publication with id " + request.getPublicationId() + " does not exist."));

        PublicationOccurrence occurrence = new PublicationOccurrence();

        for (DeliveryChannel channel : request.getChannels()) {
            PublicationDelivery delivery = new PublicationDelivery();
            delivery.setChannel(channel);
            occurrence.addDelivery(delivery);
        }

        occurrence.setPublication(publication);
        occurrence.setScheduledAt(request.getScheduledAt());

        return publicationOccurrenceRepository.save(occurrence);
    }
}
