package fr.maximechazard.agorapilot.back.campaign;

import fr.maximechazard.agorapilot.back.campaign.dtos.CampaignDTO;
import fr.maximechazard.agorapilot.back.publication.PublicationMapper;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class CampaignMapper {
    private final PublicationMapper publicationMapper;

    public CampaignDTO toDTO(Campaign campaign) {
        List<PublicationDTO> publications = new ArrayList<>();

        if (campaign.getPublications() != null && !campaign.getPublications().isEmpty()) {
            // Une publication supprimée mais archivée pour son historique n'a plus sa place dans la campagne.
            publications = campaign.getPublications().stream()
                    .filter(publication -> !Boolean.TRUE.equals(publication.getArchived()))
                    .map(publicationMapper::toDTO)
                    .toList();
        }

        return new CampaignDTO(
                campaign.getId(),
                campaign.getName(),
                campaign.getDescription(),
                campaign.getStartDate(),
                campaign.getEndDate(),
                campaign.getStatus(),
                publications
        );
    }
}
