package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.campaign.Campaign;
import fr.maximechazard.agorapilot.back.campaign.CampaignRepository;
import fr.maximechazard.agorapilot.back.campaign.exceptions.CampaignNotFoundException;
import fr.maximechazard.agorapilot.back.media.Media;
import fr.maximechazard.agorapilot.back.media.MediaRepository;
import fr.maximechazard.agorapilot.back.media.exceptions.MediaNotFoundException;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDTO;
import fr.maximechazard.agorapilot.back.publication.exceptions.DuplicateMediaException;
import fr.maximechazard.agorapilot.back.publication.exceptions.OccurrenceNotModifiableException;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotFoundException;
import fr.maximechazard.agorapilot.back.publication.exceptions.VerificationNotAllowedException;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationRepository;
import fr.maximechazard.agorapilot.back.publication.requests.CreatePublicationRequest;
import fr.maximechazard.agorapilot.back.publication.requests.UpdatePublicationRequest;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Comparator;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class PublicationService {
    private final PublicationRepository publicationRepository;
    private final MediaRepository mediaRepository;
    private final CampaignRepository campaignRepository;
    private final PublicationOccurrenceService publicationOccurrenceService;
    private final PublicationMapper mapper;

    /**
     * @param canVerify l'auteur détient {@code publications:verify} ; sans lui, la
     *                  publication ne peut naître que brouillon
     * @throws VerificationNotAllowedException une publication vérifiée demandée sans le droit
     */
    public PublicationDTO create(CreatePublicationRequest request, boolean canVerify) {
        if (request.getStatus() == PublicationStatus.VERIFIED && !canVerify) {
            throw new VerificationNotAllowedException();
        }

        Publication publication = new Publication(request.getContent(), request.getStatus());
        return mapper.toDTO(publicationRepository.save(publication));
    }

    public Iterable<PublicationDTO> getAll() {
        List<Publication> list = publicationRepository.findAllByArchivedFalse();
        return list.stream().map(mapper::toDTO).toList();
    }

    /**
     * Remplace le texte et le statut de la publication.
     * <p>
     * Les diffusions encore programmées partiront avec le nouveau texte : le
     * publisher le lit au moment de diffuser. Celles déjà publiées ne changent
     * pas sur les canaux distants.
     * <p>
     * Sans {@code publications:verify}, le statut ne peut pas changer, et
     * modifier le texte d'une publication vérifiée la repasse en brouillon : ce
     * qui a été vérifié, c'est l'ancien texte. Ses diffusions programmées ne
     * partiront pas tant qu'elle n'est pas vérifiée à nouveau.
     *
     * @throws VerificationNotAllowedException un changement de statut demandé sans le droit
     */
    @Transactional
    public PublicationDTO update(Long publicationId, UpdatePublicationRequest request, boolean canVerify) {
        Publication publication = findOrThrow(publicationId);

        if (!canVerify && request.getStatus() != publication.getStatus()) {
            throw new VerificationNotAllowedException();
        }

        boolean contentChanged = !publication.getContent().equals(request.getContent());
        publication.setContent(request.getContent());
        publication.setStatus(request.getStatus());

        if (contentChanged && !canVerify) {
            publication.setStatus(PublicationStatus.DRAFT);
        }

        return mapper.toDTO(publicationRepository.save(publication));
    }

    /**
     * Supprime la publication. Ses diffusions programmées sont annulées, et ses
     * échecs jamais publiés nulle part disparaissent avec elle.
     * <p>
     * Une publication déjà diffusée n'est pas effacée mais archivée : ses
     * diffusions restent visibles dans le calendrier comme historique de ce qui
     * est parti. Pour l'utilisateur, l'effet est le même — elle quitte la liste
     * et n'est plus modifiable.
     *
     * @throws PublicationNotFoundException     la publication n'existe pas, ou plus
     * @throws OccurrenceNotModifiableException une diffusion est en cours ou imminente
     */
    @Transactional
    public void delete(Long publicationId) {
        Publication publication = findOrThrow(publicationId);

        boolean hasHistory = publicationOccurrenceService.releaseOccurrencesOf(publication);

        if (hasHistory) {
            publication.setArchived(true);
            publicationRepository.save(publication);
        } else {
            publicationRepository.delete(publication);
        }
    }

    /**
     * Remplace la liste ordonnée des médias d'une publication.
     * <p>
     * Remplacement complet plutôt qu'ajouts et retraits unitaires : réordonner
     * une sélection tient alors en un seul appel, et l'opération est idempotente.
     * <p>
     * Les rattachements déjà présents sont <strong>réutilisés</strong> et leur seule
     * position change. Les vider pour tout recréer produirait, sur un simple
     * réordonnancement, des INSERT que Hibernate exécute avant les DELETE — et la
     * contrainte d'unicité {@code (publication_id, media_id)} sauterait.
     */
    /**
     * Comme pour le texte, changer les visuels d'une publication vérifiée sans
     * {@code publications:verify} la repasse en brouillon.
     */
    @Transactional
    public PublicationDTO setMedias(Long publicationId, List<Long> mediaIds, boolean canVerify) {
        Publication publication = findOrThrow(publicationId);
        List<Long> before = publication.getMedias().stream().map(attachment -> attachment.getMedia().getId()).toList();

        List<Long> ids = mediaIds == null ? List.of() : mediaIds;
        rejectDuplicates(ids);

        // Tous les médias sont résolus avant la moindre modification : un
        // identifiant inconnu ne doit pas laisser une sélection à moitié appliquée.
        List<Media> resolved = ids.stream().map(this::findMediaOrThrow).toList();

        List<PublicationMedia> attachments = publication.getMedias();

        // Retraits d'abord : `orphanRemoval` les traduit en DELETE.
        Set<Long> keep = new HashSet<>(ids);
        attachments.removeIf(attachment -> !keep.contains(attachment.getMedia().getId()));

        Map<Long, PublicationMedia> existing = attachments.stream()
                .collect(Collectors.toMap(attachment -> attachment.getMedia().getId(), attachment -> attachment));

        int position = 0;
        for (Media media : resolved) {
            PublicationMedia attachment = existing.get(media.getId());

            if (attachment != null) {
                attachment.setPosition(position);
            } else {
                attachments.add(new PublicationMedia(publication, media, position));
            }

            position++;
        }

        // La liste en mémoire doit refléter l'ordre que `@OrderBy` donnera à la
        // relecture, sinon le DTO renvoyé juste après ne serait pas dans l'ordre
        // demandé.
        attachments.sort(Comparator.comparing(PublicationMedia::getPosition));

        if (!canVerify && !before.equals(ids)) {
            publication.setStatus(PublicationStatus.DRAFT);
        }

        return mapper.toDTO(publicationRepository.save(publication));
    }

    /**
     * Rattache la publication à une campagne, ou l'en détache avec un
     * {@code campaignId} nul.
     * <p>
     * Seule la publication est enregistrée : c'est elle qui porte la clé
     * étrangère. Passer par {@code Campaign.addPublication} rendrait la campagne
     * sale et Hibernate la revaliderait sans raison.
     */
    @Transactional
    public PublicationDTO setCampaign(Long publicationId, Long campaignId) {
        Publication publication = findOrThrow(publicationId);

        Campaign campaign = campaignId == null ? null : campaignRepository.findById(campaignId)
                .orElseThrow(() -> new CampaignNotFoundException("Campagne " + campaignId + " introuvable"));

        publication.setCampaign(campaign);

        return mapper.toDTO(publicationRepository.save(publication));
    }

    /** Une publication archivée est traitée comme absente. */
    private Publication findOrThrow(Long publicationId) {
        return publicationRepository.findByIdAndArchivedFalse(publicationId)
                .orElseThrow(() -> new PublicationNotFoundException("Publication " + publicationId + " introuvable"));
    }

    private Media findMediaOrThrow(Long mediaId) {
        return mediaRepository.findById(mediaId)
                .orElseThrow(() -> new MediaNotFoundException("Média " + mediaId + " introuvable"));
    }

    private void rejectDuplicates(List<Long> mediaIds) {
        Set<Long> seen = new HashSet<>();

        for (Long mediaId : mediaIds) {
            if (!seen.add(mediaId)) {
                throw new DuplicateMediaException("Le média " + mediaId + " est présent deux fois dans la liste");
            }
        }
    }
}
