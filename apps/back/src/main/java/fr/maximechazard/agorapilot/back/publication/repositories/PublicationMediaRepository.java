package fr.maximechazard.agorapilot.back.publication.repositories;

import fr.maximechazard.agorapilot.back.publication.PublicationMedia;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PublicationMediaRepository extends JpaRepository<PublicationMedia, Long> {

    /** Sert à refuser la suppression d'un média encore rattaché à une publication. */
    boolean existsByMediaId(Long mediaId);

    long countByMediaId(Long mediaId);
}
