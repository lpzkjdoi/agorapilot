package fr.maximechazard.agorapilot.back.service;

import fr.maximechazard.agorapilot.back.model.Publication;
import fr.maximechazard.agorapilot.back.repository.PublicationRepository;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@AllArgsConstructor
public class PublicationService {
    private final PublicationRepository publicationRepository;

    public Iterable<Publication> getAll() {
        return publicationRepository.findAll();
    }
}
