package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.publisher.suspension.ChannelSuspension;
import fr.maximechazard.agorapilot.back.publisher.suspension.ChannelSuspensionService;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * Une diffusion est retenue quand son heure est passée et qu'un canal suspendu
 * l'empêche de partir — et seulement dans ce cas.
 */
class OccurrenceHoldTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 11, 17, 18, 20);
    private static final Clock CLOCK = Clock.fixed(NOW.atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    private final ChannelSuspensionService suspensions = mock(ChannelSuspensionService.class);
    private final OccurrenceHold hold = new OccurrenceHold(suspensions, CLOCK);

    private static PublicationOccurrence occurrence(LocalDateTime scheduledAt, PublicationOccurrenceStatus status,
                                                    DeliveryStatus deliveryStatus) {
        PublicationOccurrence occurrence = new PublicationOccurrence();
        occurrence.setScheduledAt(scheduledAt);
        occurrence.setStatus(status);
        PublicationDelivery delivery = new PublicationDelivery();
        delivery.setChannel(DeliveryChannel.FACEBOOK);
        delivery.setStatus(deliveryStatus);
        occurrence.addDelivery(delivery);
        return occurrence;
    }

    private void suspendFacebook() {
        when(suspensions.find(DeliveryChannel.FACEBOOK)).thenReturn(Optional.of(
                new ChannelSuspension(DeliveryChannel.FACEBOOK, "Bloqué", NOW.minusHours(2))));
    }

    @Test
    void holds_a_past_pending_occurrence_on_a_suspended_channel() {
        suspendFacebook();

        assertThat(hold.isHeld(occurrence(NOW.minusMinutes(20), PublicationOccurrenceStatus.SCHEDULED, DeliveryStatus.PENDING)))
                .isTrue();
    }

    @Test
    void does_not_hold_an_upcoming_occurrence() {
        suspendFacebook();

        assertThat(hold.isHeld(occurrence(NOW.plusMinutes(20), PublicationOccurrenceStatus.SCHEDULED, DeliveryStatus.PENDING)))
                .isFalse();
    }

    @Test
    void does_not_hold_an_occurrence_whose_channel_is_not_suspended() {
        when(suspensions.find(DeliveryChannel.FACEBOOK)).thenReturn(Optional.empty());

        assertThat(hold.isHeld(occurrence(NOW.minusMinutes(20), PublicationOccurrenceStatus.SCHEDULED, DeliveryStatus.PENDING)))
                .isFalse();
    }

    @Test
    void does_not_hold_an_occurrence_already_taken_in_charge_or_settled() {
        suspendFacebook();

        assertThat(hold.isHeld(occurrence(NOW.minusMinutes(20), PublicationOccurrenceStatus.SCHEDULED, DeliveryStatus.IN_PROGRESS)))
                .isFalse();
        assertThat(hold.isHeld(occurrence(NOW.minusMinutes(20), PublicationOccurrenceStatus.FAILED, DeliveryStatus.FAILED)))
                .isFalse();
    }
}
