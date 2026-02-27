package fr.maximechazard.agorapilot.back.service;

import fr.maximechazard.agorapilot.back.model.Publication;
import fr.maximechazard.agorapilot.back.repository.PublicationRepository;
import fr.maximechazard.agorapilot.back.request.CreatePublicationRequest;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class PublicationService {
    private final PublicationRepository publicationRepository;

    public Publication create(CreatePublicationRequest request) {
        Publication publication = new Publication(request.getContent(), request.getScheduledAt(), request.getPublishedAt(), request.getEndAt());
        return publicationRepository.save(publication);
    }

    public Iterable<Publication> getAll() {
        return publicationRepository.findAll();
    }
}
