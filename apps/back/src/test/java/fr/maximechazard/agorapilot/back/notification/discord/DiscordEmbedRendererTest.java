package fr.maximechazard.agorapilot.back.notification.discord;

import fr.maximechazard.agorapilot.back.notification.Notification;
import fr.maximechazard.agorapilot.back.notification.NotificationEntry;
import fr.maximechazard.agorapilot.back.notification.NotificationField;
import fr.maximechazard.agorapilot.back.notification.NotificationProperties;
import fr.maximechazard.agorapilot.back.notification.NotificationSeverity;
import fr.maximechazard.agorapilot.back.notification.NotificationType;
import org.junit.jupiter.api.Test;

import java.time.Instant;
import java.util.List;

import static fr.maximechazard.agorapilot.back.notification.NotificationFixtures.NOW;
import static org.assertj.core.api.Assertions.assertThat;

class DiscordEmbedRendererTest {

    private final DiscordEmbedRenderer renderer =
            new DiscordEmbedRenderer(new NotificationProperties(true, "preprod", List.of()));

    private static Notification.Builder failure() {
        return Notification.builder(NotificationType.DELIVERY_FAILED, NotificationSeverity.ERROR, "Échec de diffusion")
                .occurredAt(NOW);
    }

    @Test
    void renders_a_card_named_after_the_environment() {
        DiscordWebhookPayload payload = renderer.render(failure()
                .summary("Cette publication n'a pas pu être diffusée.")
                .link("https://agorapilot.test/calendrier")
                .reference("occurrence #318")
                .build());

        assertThat(payload.username()).isEqualTo("AgoraPilot · preprod");
        assertThat(payload.allowed_mentions().parse()).isEmpty();
        assertThat(payload.content()).isNull();

        DiscordWebhookPayload.Embed embed = payload.embeds().getFirst();
        assertThat(embed.title()).isEqualTo("🚨 Échec de diffusion");
        assertThat(embed.url()).isEqualTo("https://agorapilot.test/calendrier");
        assertThat(embed.color()).isEqualTo(0xEF4444);
        assertThat(embed.footer().text()).isEqualTo("AgoraPilot · preprod · échec de diffusion · occurrence #318");
        assertThat(embed.timestamp()).isEqualTo("2026-11-15T06:00:00Z");
        assertThat(embed.fields()).isNull();
    }

    @Test
    void omits_the_environment_when_none_is_set() {
        DiscordEmbedRenderer anonymous = new DiscordEmbedRenderer(new NotificationProperties(true, "", List.of()));

        DiscordWebhookPayload payload = anonymous.render(failure().build());

        assertThat(payload.username()).isEqualTo("AgoraPilot");
        assertThat(payload.embeds().getFirst().footer().text()).isEqualTo("AgoraPilot · échec de diffusion");
    }

    @Test
    void gives_each_severity_its_color() {
        assertThat(DiscordEmbedRenderer.color(NotificationSeverity.INFO)).isEqualTo(3900150);
        assertThat(DiscordEmbedRenderer.color(NotificationSeverity.SUCCESS)).isEqualTo(2278750);
        assertThat(DiscordEmbedRenderer.color(NotificationSeverity.WARNING)).isEqualTo(16096779);
        assertThat(DiscordEmbedRenderer.color(NotificationSeverity.ERROR)).isEqualTo(15680580);
    }

    /** L'heure en {@code <t:…>} : Discord l'affiche dans le fuseau de chaque lecteur. */
    @Test
    void renders_entries_with_discord_timestamps_links_and_subtext() {
        Instant at = Instant.parse("2026-11-15T09:30:00Z");

        String description = renderer.render(failure()
                .summary("3 diffusions prévues aujourd'hui.")
                .entry(new NotificationEntry(at, "Marché de Noël", "Facebook", "https://agorapilot.test/calendrier"))
                .entry(new NotificationEntry(null, "Sans heure", null, null))
                .build()).embeds().getFirst().description();

        assertThat(description).isEqualTo("""
                3 diffusions prévues aujourd'hui.

                **<t:1794735000:t>** · <t:1794735000:R>
                **[Marché de Noël](https://agorapilot.test/calendrier)**
                -# Facebook

                **Sans heure**""");
    }

    /**
     * Le texte vient des publications : il ne doit ni casser la mise en forme, ni
     * glisser un faux horodatage ou un lien. Les mentions, elles, sont coupées
     * par {@code allowed_mentions}.
     */
    @Test
    void escapes_the_markdown_of_user_text() {
        String description = renderer.render(failure()
                .entry(new NotificationEntry(null, "**Grand** [loto](https://pirate.test) <t:0:R>", "sur\nplusieurs lignes", null))
                .build()).embeds().getFirst().description();

        assertThat(description).isEqualTo(
                "**\\*\\*Grand\\*\\* \\[loto\\](https://pirate.test) \\<t:0:R\\>**\n-# sur plusieurs lignes");
    }

    @Test
    void renders_inline_fields_and_raw_error_messages_in_code_blocks() {
        List<DiscordWebhookPayload.Field> fields = renderer.render(failure()
                .field(NotificationField.inline("Prévues", "3"))
                .field(NotificationField.code("Motif", "(#190) token *expired* ```"))
                .build()).embeds().getFirst().fields();

        assertThat(fields).containsExactly(
                new DiscordWebhookPayload.Field("Prévues", "3", true),
                new DiscordWebhookPayload.Field("Motif", "```\n(#190) token *expired* '''\n```", null));
    }

    /** Discord refuse un champ vide : le message entier serait perdu. */
    @Test
    void replaces_an_empty_field_value_with_a_dash() {
        List<DiscordWebhookPayload.Field> fields = renderer.render(failure()
                .field(NotificationField.inline("Canaux", ""))
                .field(NotificationField.code("Motif", null))
                .build()).embeds().getFirst().fields();

        assertThat(fields).extracting(DiscordWebhookPayload.Field::value).containsExactly("—", "```\n—\n```");
    }

    @Test
    void stays_within_discord_limits() {
        Notification.Builder busy = failure().summary("Beaucoup de diffusions.");
        for (int i = 0; i < 200; i++) {
            busy.entry(new NotificationEntry(NOW, "Publication " + i + " " + "x".repeat(60), "Facebook", null));
        }
        for (int i = 0; i < 30; i++) {
            busy.field(NotificationField.code("Motif " + i, "e".repeat(2000)));
        }

        DiscordWebhookPayload.Embed embed = renderer.render(busy.build()).embeds().getFirst();

        assertThat(embed.description()).hasSizeLessThanOrEqualTo(DiscordEmbedRenderer.DESCRIPTION_MAX);
        assertThat(embed.description()).matches("(?s).*\\n\\n… et \\d+ autres$");
        assertThat(embed.fields()).allSatisfy(field ->
                assertThat(field.value()).hasSizeLessThanOrEqualTo(DiscordEmbedRenderer.FIELD_VALUE_MAX));

        int total = embed.title().length() + embed.description().length() + embed.footer().text().length()
                + (embed.fields() == null ? 0 : embed.fields().stream()
                        .mapToInt(field -> field.name().length() + field.value().length()).sum());
        assertThat(total).isLessThanOrEqualTo(DiscordEmbedRenderer.EMBED_TOTAL_MAX);
    }

    @Test
    void truncates_without_leaving_a_dangling_escape() {
        assertThat(DiscordEmbedRenderer.truncate("abc\\*def", 5)).isEqualTo("abc…");
        assertThat(DiscordEmbedRenderer.truncate("court", 10)).isEqualTo("court");
    }
}
