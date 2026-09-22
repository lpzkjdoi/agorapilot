package fr.maximechazard.agorapilot.back.campaign;

import fr.maximechazard.agorapilot.back.campaign.dtos.CampaignDTO;
import fr.maximechazard.agorapilot.back.campaign.requests.CreateCampaignRequest;
import fr.maximechazard.agorapilot.back.publication.Publication;
import fr.maximechazard.agorapilot.back.publication.requests.CreatePublicationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

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
                Publication publication = new Publication(p.getContent(), p.getStatus());
                campaign.addPublication(publication);
            }
        }

        return mapper.toDTO(campaignRepository.save(campaign));
    }

    public List<CampaignDTO> getAll() {
        return campaignRepository.findAll().stream().map(mapper::toDTO).toList();
    }
}
