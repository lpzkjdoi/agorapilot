package fr.maximechazard.agorapilot.back.publication.repositories;

import fr.maximechazard.agorapilot.back.publication.PublicationDelivery;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface PublicationDeliveryRepository extends JpaRepository<PublicationDelivery, Long> {
}
