package fr.maximechazard.agorapilot.back.publisher;

import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import fr.maximechazard.agorapilot.back.publication.PublicationOccurrence;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PublisherRegistryTest {

    private record StubPublisher(DeliveryChannel channel) implements Publisher {
        @Override
        public DeliveryChannel supports() {
            return channel;
        }

        @Override
        public void publish(PublicationOccurrence occurrence) {
            // no-op
        }
    }

    @Test
    void resolves_the_publisher_declared_for_a_channel() {
        StubPublisher facebook = new StubPublisher(DeliveryChannel.FACEBOOK);
        PublisherRegistry registry = new PublisherRegistry(List.of(facebook));

        assertThat(registry.forChannel(DeliveryChannel.FACEBOOK)).containsSame(facebook);
    }

    @Test
    void returns_empty_for_a_channel_without_publisher() {
        PublisherRegistry registry = new PublisherRegistry(List.of(new StubPublisher(DeliveryChannel.FACEBOOK)));

        assertThat(registry.forChannel(DeliveryChannel.INTRAMUROS)).isEmpty();
    }
}
