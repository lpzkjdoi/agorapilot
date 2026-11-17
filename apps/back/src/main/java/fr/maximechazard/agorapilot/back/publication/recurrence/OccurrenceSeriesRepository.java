package fr.maximechazard.agorapilot.back.publication.recurrence;

import fr.maximechazard.agorapilot.back.publication.Publication;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
public interface OccurrenceSeriesRepository extends JpaRepository<OccurrenceSeries, Long> {

    List<OccurrenceSeries> findAllByStatus(OccurrenceSeriesStatus status);

    List<OccurrenceSeries> findAllByPublication(Publication publication);

    List<OccurrenceSeries> findAllByPublicationIdOrderByCreatedAtDesc(Long publicationId);

    List<OccurrenceSeries> findAllByPublicationCampaignIdAndStatus(Long campaignId, OccurrenceSeriesStatus status);
}
