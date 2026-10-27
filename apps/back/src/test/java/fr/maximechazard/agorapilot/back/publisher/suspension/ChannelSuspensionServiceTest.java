package fr.maximechazard.agorapilot.back.publisher.suspension;

import fr.maximechazard.agorapilot.back.account.events.SecurityEventService;
import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ChannelSuspensionServiceTest {

    private static final ZoneId PARIS = ZoneId.of("Europe/Paris");

    @Mock
    private ChannelSuspensionRepository repository;

    private ChannelSuspensionService service;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-10-16T12:05:00Z"), PARIS);
        service = new ChannelSuspensionService(repository, mock(SecurityEventService.class), clock);
    }

    private ChannelSuspension existing() {
        return new ChannelSuspension(DeliveryChannel.FACEBOOK, "Confirmez votre identité (Facebook, code 368)",
                LocalDateTime.of(2026, 10, 16, 14, 5));
    }

    @Test
    void records_the_reason_and_the_time_of_the_suspension() {
        when(repository.existsById(DeliveryChannel.FACEBOOK)).thenReturn(false);

        service.suspend(DeliveryChannel.FACEBOOK, "Confirmez votre identité (Facebook, code 368)");

        ArgumentCaptor<ChannelSuspension> saved = ArgumentCaptor.forClass(ChannelSuspension.class);
        verify(repository).save(saved.capture());
        assertThat(saved.getValue()).isEqualTo(existing());
    }

    /** Le premier refus explique le blocage, et sa date dit depuis quand les diffusions attendent. */
    @Test
    void keeps_the_first_suspension_when_already_suspended() {
        when(repository.existsById(DeliveryChannel.FACEBOOK)).thenReturn(true);

        service.suspend(DeliveryChannel.FACEBOOK, "Error validating access token (Facebook, code 190)");

        verify(repository, never()).save(any());
    }

    @Test
    void lifts_an_existing_suspension() {
        ChannelSuspension suspension = existing();
        when(repository.findById(DeliveryChannel.FACEBOOK)).thenReturn(Optional.of(suspension));

        service.lift(DeliveryChannel.FACEBOOK);

        verify(repository).delete(suspension);
    }

    @Test
    void lifting_a_channel_that_is_not_suspended_does_nothing() {
        when(repository.findById(DeliveryChannel.FACEBOOK)).thenReturn(Optional.empty());

        service.lift(DeliveryChannel.FACEBOOK);

        verify(repository, never()).delete(any());
    }

    @Test
    void refuses_an_immediate_delivery_on_a_suspended_channel() {
        when(repository.findById(DeliveryChannel.FACEBOOK)).thenReturn(Optional.of(existing()));

        assertThatThrownBy(() -> service.ensureNotSuspended(DeliveryChannel.FACEBOOK))
                .isInstanceOf(ChannelSuspendedException.class)
                .hasMessage("Diffusions Facebook suspendues depuis le 16/10/2026 à 14:05 : "
                        + "Confirmez votre identité (Facebook, code 368)"
                        + " Levez la suspension une fois le problème réglé sur Facebook.");
    }

    @Test
    void lets_an_immediate_delivery_through_when_the_channel_is_not_suspended() {
        when(repository.findById(DeliveryChannel.FACEBOOK)).thenReturn(Optional.empty());

        assertThatCode(() -> service.ensureNotSuspended(DeliveryChannel.FACEBOOK)).doesNotThrowAnyException();
    }
}
