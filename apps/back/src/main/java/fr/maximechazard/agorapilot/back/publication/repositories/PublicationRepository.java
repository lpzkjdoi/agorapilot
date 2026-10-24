package fr.maximechazard.agorapilot.back.publication.repositories;

import fr.maximechazard.agorapilot.back.publication.Publication;
import fr.maximechazard.agorapilot.back.publication.PublicationStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PublicationRepository extends JpaRepository<Publication, Long> {
    long countByStatusAndArchivedFalse(PublicationStatus status);

    /**
     * Une publication archivée est une publication supprimée qu'on garde pour
     * l'historique de ses diffusions : elle n'est plus ni listée ni modifiable.
     */
    List<Publication> findAllByArchivedFalse();

    Optional<Publication> findByIdAndArchivedFalse(Long id);
}
