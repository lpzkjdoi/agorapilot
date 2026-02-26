package fr.maximechazard.agorapilot.back.repository;

import fr.maximechazard.agorapilot.back.model.Publication;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PublicationRepository extends JpaRepository<Publication, Long> {
}
