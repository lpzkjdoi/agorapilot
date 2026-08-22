package fr.maximechazard.agorapilot.back.publication.dtos.mappers;

import fr.maximechazard.agorapilot.back.publication.PublicationMapper;
import fr.maximechazard.agorapilot.back.publication.PublicationOccurrence;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDeliveryDTO;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationOccurrenceDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicationOccurrenceMapper {
    private final PublicationMapper publicationMapper;
    private final PublicationDeliveryMapper publicationDeliveryMapper;

    public PublicationOccurrenceDTO toDTO(PublicationOccurrence occurrence) {
        List<PublicationDeliveryDTO> deliveries = occurrence.getDeliveries().stream()
                .map(publicationDeliveryMapper::toDTO)
                .toList();

        return new PublicationOccurrenceDTO(
                occurrence.getId(),
                occurrence.getScheduledAt(),
                publicationMapper.toDTO(occurrence.getPublication()),
                deliveries
        );
    }
}
