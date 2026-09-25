package fr.maximechazard.agorapilot.back.publication.repositories;

import fr.maximechazard.agorapilot.back.publication.PublicationOccurrence;
import fr.maximechazard.agorapilot.back.publication.PublicationOccurrenceStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

@Repository
public interface PublicationOccurrenceRepository extends JpaRepository<PublicationOccurrence, Long> {
    /** Intervalle semi-ouvert {@code [start, end)} : {@code Between} inclurait la borne haute. */
    List<PublicationOccurrence> findAllByScheduledAtGreaterThanEqualAndScheduledAtLessThan(LocalDateTime start, LocalDateTime end);

    List<PublicationOccurrence> findAllByScheduledAtBeforeAndStatus(LocalDateTime scheduledAt, PublicationOccurrenceStatus status);
}
