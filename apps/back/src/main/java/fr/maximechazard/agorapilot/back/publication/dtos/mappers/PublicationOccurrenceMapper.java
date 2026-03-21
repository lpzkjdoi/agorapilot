package fr.maximechazard.agorapilot.back.publication.dtos.mappers;

import fr.maximechazard.agorapilot.back.publication.PublicationMapper;
import fr.maximechazard.agorapilot.back.publication.PublicationOccurrence;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationOccurrenceDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PublicationOccurrenceMapper {
    private final PublicationMapper publicationMapper;

    public PublicationOccurrenceDTO toDTO(PublicationOccurrence occurrence) {
        return new PublicationOccurrenceDTO(
                occurrence.getId(),
                occurrence.getScheduledAt(),
                publicationMapper.toDTO(occurrence.getPublication()),
                occurrence.getDeliveries()
        );
    }
}
