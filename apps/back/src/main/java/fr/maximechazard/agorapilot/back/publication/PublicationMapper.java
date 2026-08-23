package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.media.MediaMapper;
import fr.maximechazard.agorapilot.back.media.dtos.MediaSummaryDTO;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class PublicationMapper {
    private final MediaMapper mediaMapper;

    public PublicationDTO toDTO(Publication publication) {
        List<MediaSummaryDTO> medias = publication.getMedias() == null
                ? List.of()
                : publication.getMedias().stream()
                             .map(PublicationMedia::getMedia)
                             .map(mediaMapper::toSummaryDTO)
                             .toList();

        return new PublicationDTO(
                publication.getId(),
                publication.getContent(),
                publication.getStatus(),
                medias
        );
    }
}
