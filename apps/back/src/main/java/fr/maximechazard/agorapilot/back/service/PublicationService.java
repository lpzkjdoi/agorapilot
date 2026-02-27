package fr.maximechazard.agorapilot.back.service;

import fr.maximechazard.agorapilot.back.dto.mapper.PublicationDTO;
import fr.maximechazard.agorapilot.back.dto.mapper.PublicationMapper;
import fr.maximechazard.agorapilot.back.model.Publication;
import fr.maximechazard.agorapilot.back.repository.PublicationRepository;
import fr.maximechazard.agorapilot.back.request.CreatePublicationRequest;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class PublicationService {
    private final PublicationRepository publicationRepository;
    private final PublicationMapper mapper;

    public PublicationDTO create(CreatePublicationRequest request) {
        Publication publication = new Publication(request.getContent(), request.getScheduledAt(), request.getPublishedAt(), request.getEndAt());
        return mapper.toDTO(publicationRepository.save(publication));
    }

    public Iterable<PublicationDTO> getAll() {
        List<Publication> list = publicationRepository.findAll();
        return list.stream().map(mapper::toDTO).toList();
    }
}
