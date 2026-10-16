package fr.maximechazard.agorapilot.back.publisher.suspension;

import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ChannelSuspensionRepository extends JpaRepository<ChannelSuspension, DeliveryChannel> {
}
