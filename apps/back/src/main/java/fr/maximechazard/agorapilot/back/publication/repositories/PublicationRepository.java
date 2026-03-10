package fr.maximechazard.agorapilot.back.publication.repositories;

import fr.maximechazard.agorapilot.back.publication.Publication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PublicationRepository extends JpaRepository<Publication, Long> {
}
