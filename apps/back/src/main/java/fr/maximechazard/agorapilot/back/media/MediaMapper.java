package fr.maximechazard.agorapilot.back.media;

import fr.maximechazard.agorapilot.back.media.dtos.MediaDTO;
import org.springframework.stereotype.Service;

@Service
public class MediaMapper {

    public MediaDTO toDTO(Media media) {
        return new MediaDTO(
                media.getId(),
                media.getTitle(),
                media.getOriginalFilename(),
                media.getFileType().getContentType(),
                media.getSizeBytes(),
                media.getWidth(),
                media.getHeight(),
                media.getAltText(),
                media.getChecksum(),
                media.getArchived(),
                media.getCreatedAt(),
                media.getUpdatedAt()
        );
    }
}
