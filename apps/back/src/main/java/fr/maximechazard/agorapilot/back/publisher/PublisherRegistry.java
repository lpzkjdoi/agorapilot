package fr.maximechazard.agorapilot.back.publisher;

import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Résout le {@link Publisher} capable de diffuser sur un canal donné.
 * <p>
 * Les publishers sont découverts par injection : ajouter une implémentation
 * (Intramuros, par exemple) suffit à la rendre disponible ici.
 */
@Component
public class PublisherRegistry {

    private final Map<DeliveryChannel, Publisher> publishersByChannel;

    public PublisherRegistry(List<Publisher> publishers) {
        this.publishersByChannel = publishers.stream()
                .collect(Collectors.toUnmodifiableMap(Publisher::supports, Function.identity()));
    }

    public Optional<Publisher> forChannel(DeliveryChannel channel) {
        return Optional.ofNullable(publishersByChannel.get(channel));
    }
}
