package fr.maximechazard.agorapilot.back.publisher;

import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import fr.maximechazard.agorapilot.back.publication.PublicationOccurrence;

public interface Publisher {
    DeliveryChannel supports();

    void publish(PublicationOccurrence occurrence);
}

