package fr.maximechazard.agorapilot.back.notification.discord;

import com.fasterxml.jackson.annotation.JsonInclude;

import java.util.List;

/**
 * Corps de {@code POST /api/webhooks/{id}/{token}} (« Execute Webhook »).
 * <p>
 * Les composants portent les noms de l'API Discord, {@code snake_case} compris,
 * comme les requêtes Facebook du projet. Les champs absents ne sont pas émis :
 * Discord refuse certaines valeurs {@code null}.
 *
 * @param allowed_mentions toujours vide : les textes viennent des publications, et
 *                         un « @everyone » dans une affiche ne doit pinger personne
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
public record DiscordWebhookPayload(
        String username,
        String content,
        List<Embed> embeds,
        AllowedMentions allowed_mentions
) {

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Embed(
            String title,
            String url,
            String description,
            Integer color,
            List<Field> fields,
            Footer footer,
            String timestamp
    ) {
    }

    @JsonInclude(JsonInclude.Include.NON_NULL)
    public record Field(String name, String value, Boolean inline) {
    }

    public record Footer(String text) {
    }

    public record AllowedMentions(List<String> parse) {
        public static AllowedMentions none() {
            return new AllowedMentions(List.of());
        }
    }
}
