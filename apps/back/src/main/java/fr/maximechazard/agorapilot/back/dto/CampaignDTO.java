package fr.maximechazard.agorapilot.back.dto;

import fr.maximechazard.agorapilot.back.CampaignStatus;

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
