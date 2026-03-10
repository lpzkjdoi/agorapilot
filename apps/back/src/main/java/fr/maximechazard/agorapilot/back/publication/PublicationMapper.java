package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDTO;
import org.springframework.stereotype.Service;

@Service
public class PublicationMapper {
    public PublicationDTO toDTO(Publication publication) {
        return new PublicationDTO(
                publication.getId(),
                publication.getContent(),
                publication.getStatus(),
                publication.getScheduledAt(),
                publication.getPublishedAt(),
                publication.getEndAt()
        );
    }
}
