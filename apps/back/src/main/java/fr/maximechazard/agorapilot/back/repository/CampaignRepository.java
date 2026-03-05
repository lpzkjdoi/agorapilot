package fr.maximechazard.agorapilot.back.repository;

import fr.maximechazard.agorapilot.back.model.Campaign;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface CampaignRepository extends JpaRepository<Campaign, Long> {
}
