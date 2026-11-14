package fr.maximechazard.agorapilot.back.notification;

import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class NotificationSenderFactoryTest {

    private static NotificationSender sender(NotificationChannelType type) {
        return new NotificationSender() {
            @Override
            public NotificationChannelType supports() {
                return type;
            }

            @Override
            public void send(NotificationTarget target, Notification notification) {
            }
        };
    }

    @Test
    void resolves_the_sender_of_each_channel_type() {
        NotificationSender discord = sender(NotificationChannelType.DISCORD);
        NotificationSender webhook = sender(NotificationChannelType.WEBHOOK);

        NotificationSenderFactory factory = new NotificationSenderFactory(List.of(discord, webhook));

        assertThat(factory.get(NotificationChannelType.DISCORD)).isSameAs(discord);
        assertThat(factory.get(NotificationChannelType.WEBHOOK)).isSameAs(webhook);
    }

    @Test
    void reports_a_channel_type_without_sender() {
        NotificationSenderFactory factory = new NotificationSenderFactory(List.of(sender(NotificationChannelType.DISCORD)));

        assertThat(factory.forType(NotificationChannelType.WEBHOOK)).isEmpty();
        assertThatThrownBy(() -> factory.get(NotificationChannelType.WEBHOOK))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("WEBHOOK");
    }

    /** Deux senders pour une même technologie : on refuse de démarrer plutôt que d'en choisir un. */
    @Test
    void refuses_two_senders_for_the_same_channel_type() {
        assertThatThrownBy(() -> new NotificationSenderFactory(List.of(
                sender(NotificationChannelType.DISCORD), sender(NotificationChannelType.DISCORD))))
                .isInstanceOf(IllegalStateException.class);
    }
}
