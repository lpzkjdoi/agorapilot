package fr.maximechazard.agorapilot.back.service;

import fr.maximechazard.agorapilot.back.dto.CampaignDTO;
import fr.maximechazard.agorapilot.back.dto.mapper.CampaignMapper;
import fr.maximechazard.agorapilot.back.model.Campaign;
import fr.maximechazard.agorapilot.back.model.Publication;
import fr.maximechazard.agorapilot.back.repository.CampaignRepository;
import fr.maximechazard.agorapilot.back.request.CreateCampaignRequest;
import fr.maximechazard.agorapilot.back.request.CreatePublicationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class CampaignService {
    private final CampaignRepository campaignRepository;
    private final CampaignMapper mapper;

    public CampaignDTO create(CreateCampaignRequest request) {
        Campaign campaign = new Campaign(
                request.getName(),
                request.getDescription(),
                request.getStartDate(),
                request.getEndDate()
        );

        if (!request.getPublications().isEmpty()) {
            for (CreatePublicationRequest p : request.getPublications()) {
                Publication publication = new Publication(p.getContent(), p.getScheduledAt(), p.getPublishedAt(), p.getEndAt());
                campaign.addPublication(publication);
            }
        }

        return mapper.toDTO(campaignRepository.save(campaign));
    }

    public Iterable<Campaign> getAll() {
        return campaignRepository.findAll();
    }
}
