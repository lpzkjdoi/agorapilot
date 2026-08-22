package fr.maximechazard.agorapilot.back.publication.dtos.mappers;

import fr.maximechazard.agorapilot.back.publication.PublicationDelivery;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDeliveryDTO;
import org.springframework.stereotype.Service;

@Service
public class PublicationDeliveryMapper {
    public PublicationDeliveryDTO toDTO(PublicationDelivery delivery) {
        return new PublicationDeliveryDTO(
                delivery.getId(),
                delivery.getOccurrence().getId(),
                delivery.getChannel(),
                delivery.getStatus(),
                delivery.getPublishedAt(),
                delivery.getExternalId()
        );
    }
}
