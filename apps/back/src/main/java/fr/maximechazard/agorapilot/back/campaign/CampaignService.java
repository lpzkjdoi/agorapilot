package fr.maximechazard.agorapilot.back.campaign;

import fr.maximechazard.agorapilot.back.campaign.dtos.CampaignDTO;
import fr.maximechazard.agorapilot.back.campaign.exceptions.CampaignNotCancelableException;
import fr.maximechazard.agorapilot.back.campaign.exceptions.CampaignNotClosableException;
import fr.maximechazard.agorapilot.back.campaign.exceptions.CampaignNotFoundException;
import fr.maximechazard.agorapilot.back.campaign.requests.CreateCampaignRequest;
import fr.maximechazard.agorapilot.back.campaign.requests.UpdateCampaignRequest;
import fr.maximechazard.agorapilot.back.publication.PublicationOccurrenceService;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationOccurrenceDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

/**
 * Une campagne archivée est traitée partout comme inexistante : {@code 404} en
 * lecture comme en écriture.
 */
@Service
@RequiredArgsConstructor
public class CampaignService {
    private final CampaignRepository campaignRepository;
    private final CampaignMapper mapper;
    private final PublicationOccurrenceService publicationOccurrenceService;

    public CampaignDTO create(CreateCampaignRequest request) {
        Campaign campaign = new Campaign(
                request.getName(),
                descriptionOf(request.getDescription()),
                request.getStartDate(),
                request.getEndDate()
        );

        // Une campagne naît vide : ses publications s'y rattachent ensuite, avec les
        // droits et les règles de statut qui s'appliquent aux publications.

        return mapper.toDTO(campaignRepository.save(campaign));
    }

    @Transactional(readOnly = true)
    public List<CampaignDTO> getAll() {
        return campaignRepository.findAllByArchivedFalse().stream().map(mapper::toDTO).toList();
    }

    @Transactional(readOnly = true)
    public CampaignDTO getById(Long campaignId) {
        return mapper.toDTO(find(campaignId));
    }

    /** Les diffusions de la campagne, pour suivre sa planification ; {@code 404} si elle n'existe pas. */
    @Transactional(readOnly = true)
    public List<PublicationOccurrenceDTO> getOccurrences(Long campaignId) {
        find(campaignId);
        return publicationOccurrenceService.findByCampaign(campaignId);
    }

    /**
     * Remplace nom, description et période. Une campagne clôturée ou annulée le
     * reste : la modification ne revient pas sur cette décision.
     */
    @Transactional
    public CampaignDTO update(Long campaignId, UpdateCampaignRequest request) {
        Campaign campaign = find(campaignId);

        campaign.update(
                request.getName(),
                descriptionOf(request.getDescription()),
                request.getStartDate(),
                request.getEndDate()
        );

        return mapper.toDTO(campaignRepository.save(campaign));
    }

    /**
     * Clôture une campagne commencée.
     * <p>
     * Le critère est la <strong>date de début</strong> : une campagne dont la
     * date de fin est déjà passée est terminée de fait, et la clôturer ne fait
     * que l'acter.
     */
    @Transactional
    public CampaignDTO close(Long campaignId) {
        Campaign campaign = find(campaignId);

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

    /**
     * Annule une campagne qui n'est pas terminée — brouillon, programmée ou en
     * cours. Les diffusions de ses publications ne sont pas touchées.
     */
    @Transactional
    public CampaignDTO cancel(Long campaignId) {
        Campaign campaign = find(campaignId);

        switch (campaign.statusAt(LocalDateTime.now())) {
            case CANCELED -> throw new CampaignNotCancelableException("La campagne " + campaignId + " est déjà annulée");
            case COMPLETED -> throw new CampaignNotCancelableException("La campagne " + campaignId + " est terminée");
            default -> campaign.cancel();
        }

        return mapper.toDTO(campaignRepository.save(campaign));
    }

    /**
     * « Supprime » une campagne en l'archivant. Ses publications ne sont pas
     * supprimées : elles sont détachées et redeviennent sans campagne.
     */
    @Transactional
    public void archive(Long campaignId) {
        Campaign campaign = find(campaignId);

        campaign.archive();

        campaignRepository.save(campaign);
    }

    private Campaign find(Long campaignId) {
        return campaignRepository.findByIdAndArchivedFalse(campaignId)
                .orElseThrow(() -> new CampaignNotFoundException("Campagne " + campaignId + " introuvable"));
    }

    /** La colonne est {@code NOT NULL}, la description facultative. */
    private static String descriptionOf(String description) {
        return description == null ? "" : description;
    }
}
