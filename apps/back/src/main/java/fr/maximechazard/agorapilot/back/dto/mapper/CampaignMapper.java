package fr.maximechazard.agorapilot.back.dto.mapper;

import fr.maximechazard.agorapilot.back.dto.CampaignDTO;
import fr.maximechazard.agorapilot.back.dto.PublicationDTO;
import fr.maximechazard.agorapilot.back.model.Campaign;
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
            publications = campaign.getPublications().stream().map(publicationMapper::toDTO).toList();
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
