package fr.maximechazard.agorapilot.back.campaign;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Long> {

    /**
     * Les campagnes en cours à {@code now} : commencées, pas encore finies, ni
     * clôturées, annulées ou archivées.
     * <p>
     * Calculé sur les dates plutôt que sur le statut : rien ne fait passer une
     * campagne {@code SCHEDULED} à {@code ACTIVE} quand sa date de début arrive,
     * ni une campagne {@code ACTIVE} à {@code COMPLETED} à sa date de fin.
     */
    @Query("""
            select count(c) from Campaign c
            where c.archived = false
              and c.status not in (fr.maximechazard.agorapilot.back.campaign.CampaignStatus.COMPLETED,
                                   fr.maximechazard.agorapilot.back.campaign.CampaignStatus.CANCELED)
              and c.startDate <= :now
              and (c.endDate is null or c.endDate > :now)
            """)
    long countActive(@Param("now") LocalDateTime now);
}
