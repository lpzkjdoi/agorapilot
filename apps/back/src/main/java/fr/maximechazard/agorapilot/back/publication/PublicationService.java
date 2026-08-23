package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.media.Media;
import fr.maximechazard.agorapilot.back.media.MediaRepository;
import fr.maximechazard.agorapilot.back.media.exceptions.MediaNotFoundException;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDTO;
import fr.maximechazard.agorapilot.back.publication.exceptions.DuplicateMediaException;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotFoundException;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationRepository;
import fr.maximechazard.agorapilot.back.publication.requests.CreatePublicationRequest;
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
    private final PublicationMapper mapper;

    public PublicationDTO create(CreatePublicationRequest request) {
        Publication publication = new Publication(request.getContent(), request.getStatus());
        return mapper.toDTO(publicationRepository.save(publication));
    }

    public Iterable<PublicationDTO> getAll() {
        List<Publication> list = publicationRepository.findAll();
        return list.stream().map(mapper::toDTO).toList();
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
    @Transactional
    public PublicationDTO setMedias(Long publicationId, List<Long> mediaIds) {
        Publication publication = publicationRepository.findById(publicationId)
                .orElseThrow(() -> new PublicationNotFoundException("Publication " + publicationId + " introuvable"));

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

        return mapper.toDTO(publicationRepository.save(publication));
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
