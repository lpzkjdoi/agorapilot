package fr.maximechazard.agorapilot.back.publication.repositories;

import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import fr.maximechazard.agorapilot.back.publication.PublicationDelivery;
import fr.maximechazard.agorapilot.back.publication.PublicationOccurrence;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.Optional;

@Repository
public interface PublicationDeliveryRepository extends JpaRepository<PublicationDelivery, Long> {
    Optional<PublicationDelivery> findByOccurrenceAndChannel(PublicationOccurrence occurrence, DeliveryChannel channel);

    List<PublicationDelivery> findAllByOccurrence(PublicationOccurrence occurrence);
}
