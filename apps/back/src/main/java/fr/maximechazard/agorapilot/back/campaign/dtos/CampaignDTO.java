package fr.maximechazard.agorapilot.back.campaign.dtos;

import fr.maximechazard.agorapilot.back.campaign.CampaignStatus;
import fr.maximechazard.agorapilot.back.dto.PublicationDTO;

import java.time.LocalDateTime;
import java.util.List;

public record CampaignDTO(
        Long id,
        String name,
        String description,
        LocalDateTime startDate,
        LocalDateTime endDate,
        CampaignStatus status,
        List<PublicationDTO> publications
) {
}
