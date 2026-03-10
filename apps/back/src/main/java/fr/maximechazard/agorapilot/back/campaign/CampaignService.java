package fr.maximechazard.agorapilot.back.campaign;

import fr.maximechazard.agorapilot.back.campaign.dtos.CampaignDTO;
import fr.maximechazard.agorapilot.back.campaign.requests.CreateCampaignRequest;
import fr.maximechazard.agorapilot.back.publication.Publication;
import fr.maximechazard.agorapilot.back.publication.requests.CreatePublicationRequest;
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

        if (request.getPublications() != null && !request.getPublications().isEmpty()) {
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
