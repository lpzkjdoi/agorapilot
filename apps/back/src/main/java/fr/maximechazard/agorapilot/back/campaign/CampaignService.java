package fr.maximechazard.agorapilot.back.campaign;

import fr.maximechazard.agorapilot.back.campaign.dtos.CampaignDTO;
import fr.maximechazard.agorapilot.back.campaign.exceptions.CampaignNotClosableException;
import fr.maximechazard.agorapilot.back.campaign.exceptions.CampaignNotFoundException;
import fr.maximechazard.agorapilot.back.campaign.requests.CreateCampaignRequest;
import fr.maximechazard.agorapilot.back.publication.Publication;
import fr.maximechazard.agorapilot.back.publication.requests.CreatePublicationRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
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

    /**
     * Clôture une campagne commencée.
     * <p>
     * Le critère est la <strong>date de début</strong>, pas le statut stocké :
     * celui-ci est figé à la création et ne bascule jamais de {@code SCHEDULED}
     * à {@code ACTIVE} de lui-même. Une campagne programmée dont la date est
     * passée a bel et bien commencé.
     */
    @Transactional
    public CampaignDTO close(Long campaignId) {
        Campaign campaign = campaignRepository.findById(campaignId)
                .orElseThrow(() -> new CampaignNotFoundException("Campagne " + campaignId + " introuvable"));

        if (campaign.getStatus() == CampaignStatus.COMPLETED) {
            throw new CampaignNotClosableException("La campagne " + campaignId + " est déjà clôturée");
        }

        if (campaign.getStatus() == CampaignStatus.CANCELED) {
            throw new CampaignNotClosableException("La campagne " + campaignId + " est annulée");
        }

        LocalDateTime now = LocalDateTime.now();

        if (!campaign.hasStarted(now)) {
            throw new CampaignNotClosableException("La campagne " + campaignId + " n'a pas encore commencé");
        }

        campaign.close(now);

        return mapper.toDTO(campaignRepository.save(campaign));
    }
}
