package fr.maximechazard.agorapilot.back.notification;

import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;

import static fr.maximechazard.agorapilot.back.notification.NotificationFixtures.discord;
import static fr.maximechazard.agorapilot.back.notification.NotificationFixtures.notification;
import static fr.maximechazard.agorapilot.back.notification.NotificationFixtures.properties;
import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.assertj.core.api.Assertions.tuple;

class NotificationServiceTest {

    private static final String PROGRAMME_URL = "https://discord.test/api/webhooks/1/programme-token";
    private static final String ALERTES_URL = "https://discord.test/api/webhooks/2/alertes-token";

    /** Retient les cibles servies ; échoue sur celles qu'on lui désigne. */
    private static final class RecordingSender implements NotificationSender {
        private final List<String> served = new ArrayList<>();
        private final List<String> failing = new ArrayList<>();

        @Override
        public NotificationChannelType supports() {
            return NotificationChannelType.DISCORD;
        }

        @Override
        public void send(NotificationTarget target, Notification notification) {
            if (failing.contains(target.name())) {
                throw new IllegalStateException("I/O error on POST request for \"" + target.url() + "?wait=true\": timeout");
            }
            served.add(target.name());
        }
    }

    private final RecordingSender sender = new RecordingSender();

    private NotificationService service(NotificationProperties properties) {
        return new NotificationService(properties, new NotificationSenderFactory(List.of(sender)));
    }

    @Test
    void sends_only_to_the_targets_following_the_notification_type() {
        NotificationService service = service(properties(
                discord("programme-du-jour", PROGRAMME_URL, NotificationType.DAILY_SUMMARY),
                discord("alertes", ALERTES_URL, NotificationType.DELIVERY_FAILED)));

        List<NotificationDispatchResult> results = service.notify(notification(NotificationType.DELIVERY_FAILED));

        assertThat(sender.served).containsExactly("alertes");
        assertThat(results).containsExactly(new NotificationDispatchResult("alertes", true, null));
    }

    /** Sans URL dans cet environnement (développement, tests), la cible est ignorée. */
    @Test
    void skips_a_target_without_url() {
        NotificationService service = service(properties(
                discord("alertes", "", NotificationType.DELIVERY_FAILED)));

        assertThat(service.notify(notification(NotificationType.DELIVERY_FAILED))).isEmpty();
        assertThat(sender.served).isEmpty();
    }

    @Test
    void sends_nothing_when_disabled() {
        NotificationService service = service(new NotificationProperties(false, "preprod",
                List.of(discord("alertes", ALERTES_URL, NotificationType.DELIVERY_FAILED))));

        assertThat(service.notify(notification(NotificationType.DELIVERY_FAILED))).isEmpty();
        assertThat(sender.served).isEmpty();
    }

    /**
     * Un salon en panne ne prive pas les autres de la notification, et l'URL du
     * webhook — un secret — n'apparaît pas dans le motif rapporté.
     */
    @Test
    void isolates_a_failing_target_and_masks_its_url() {
        NotificationService service = service(properties(
                discord("alertes", ALERTES_URL, NotificationType.DELIVERY_FAILED),
                discord("secours", PROGRAMME_URL, NotificationType.DELIVERY_FAILED)));
        sender.failing.add("alertes");

        List<NotificationDispatchResult> results = service.notify(notification(NotificationType.DELIVERY_FAILED));

        assertThat(sender.served).containsExactly("secours");
        assertThat(results)
                .extracting(NotificationDispatchResult::target, NotificationDispatchResult::delivered)
                .containsExactly(tuple("alertes", false), tuple("secours", true));
        assertThat(results.getFirst().error())
                .doesNotContain("alertes-token")
                .contains("[webhook]?wait=true");
    }

    @Test
    void reports_a_target_whose_type_has_no_sender() {
        NotificationService service = service(properties(new NotificationTarget(
                "n8n", NotificationChannelType.WEBHOOK, "https://n8n.test/hook", java.util.Set.of(NotificationType.TEST))));

        List<NotificationDispatchResult> results = service.notify(notification(NotificationType.TEST));

        assertThat(results).singleElement().satisfies(result -> {
            assertThat(result.delivered()).isFalse();
            assertThat(result.error()).contains("WEBHOOK");
        });
    }

    /** L'envoi ciblé sert à vérifier un salon : il ignore les types que ce salon suit. */
    @Test
    void sends_to_a_named_target_whatever_it_follows() {
        NotificationService service = service(properties(
                discord("alertes", ALERTES_URL, NotificationType.DELIVERY_FAILED)));

        service.notifyTarget("alertes", notification(NotificationType.TEST));

        assertThat(sender.served).containsExactly("alertes");
    }

    @Test
    void refuses_an_unknown_or_unconfigured_target() {
        NotificationService service = service(properties(
                discord("alertes", "", NotificationType.DELIVERY_FAILED)));

        assertThatThrownBy(() -> service.notifyTarget("alertes", notification(NotificationType.TEST)))
                .isInstanceOf(NotificationTargetNotFoundException.class);
        assertThatThrownBy(() -> service.notifyTarget("inconnue", notification(NotificationType.TEST)))
                .isInstanceOf(NotificationTargetNotFoundException.class);
    }
}
