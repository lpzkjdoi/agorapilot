package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDTO;
import fr.maximechazard.agorapilot.back.publication.requests.CreatePublicationRequest;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@AllArgsConstructor
public class PublicationService {
    private final PublicationRepository publicationRepository;
    private final PublicationMapper mapper;

    public PublicationDTO create(CreatePublicationRequest request) {
        Publication publication = new Publication(request.getContent());
        return mapper.toDTO(publicationRepository.save(publication));
    }

    public Iterable<PublicationDTO> getAll() {
        List<Publication> list = publicationRepository.findAll();
        return list.stream().map(mapper::toDTO).toList();
    }
}
