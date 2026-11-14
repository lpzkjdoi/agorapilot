package fr.maximechazard.agorapilot.back.notification.discord;

import fr.maximechazard.agorapilot.back.notification.Notification;
import fr.maximechazard.agorapilot.back.notification.NotificationEntry;
import fr.maximechazard.agorapilot.back.notification.NotificationField;
import fr.maximechazard.agorapilot.back.notification.NotificationProperties;
import fr.maximechazard.agorapilot.back.notification.NotificationSeverity;
import fr.maximechazard.agorapilot.back.notification.NotificationType;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.stream.Collectors;
import java.util.stream.Stream;

/**
 * Met une {@link Notification} en forme de carte Discord (embed).
 * <p>
 * Tout le texte venu du modèle est échappé : il est saisi par des humains
 * (contenu des publications) et ne doit ni casser la mise en forme, ni produire
 * une mention ou un faux horodatage. Seul ce que le renderer ajoute lui-même est
 * du markdown. Les heures sont émises en {@code <t:…>} : Discord les affiche dans
 * le fuseau de chaque lecteur.
 * <p>
 * Les limites de Discord sont appliquées ici plutôt que subies : un dépassement
 * ferait refuser le message entier (400).
 */
@Component
public class DiscordEmbedRenderer {

    static final String APP_NAME = "AgoraPilot";

    static final int TITLE_MAX = 256;
    static final int DESCRIPTION_MAX = 4096;
    static final int FIELDS_MAX = 25;
    static final int FIELD_NAME_MAX = 256;
    static final int FIELD_VALUE_MAX = 1024;
    static final int FOOTER_MAX = 2048;
    static final int EMBED_TOTAL_MAX = 6000;
    /** Place gardée en fin de description pour « … et N autres ». */
    private static final int OVERFLOW_RESERVE = 40;

    private static final Pattern MARKDOWN = Pattern.compile("[\\\\*_~`|\\[\\]<>#\\-]");
    private static final String SEPARATOR = " · ";

    private final String environment;

    public DiscordEmbedRenderer(NotificationProperties properties) {
        this.environment = properties.environment();
    }

    public DiscordWebhookPayload render(Notification notification) {
        DiscordWebhookPayload.Embed embed = new DiscordWebhookPayload.Embed(
                truncate(icon(notification.type()) + " " + escape(notification.title()), TITLE_MAX),
                notification.link(),
                description(notification),
                color(notification.severity()),
                null,
                new DiscordWebhookPayload.Footer(truncate(footer(notification), FOOTER_MAX)),
                notification.occurredAt().toString()
        );

        embed = withFields(embed, fields(notification.fields()));

        return new DiscordWebhookPayload(
                username(),
                null,
                List.of(embed),
                DiscordWebhookPayload.AllowedMentions.none()
        );
    }

    // ------------------------------ En-tête / pied --------------------------

    private String username() {
        return hasEnvironment() ? APP_NAME + SEPARATOR + environment : APP_NAME;
    }

    private String footer(Notification notification) {
        return Stream.of(APP_NAME, hasEnvironment() ? environment : null,
                        notification.type().label(), notification.reference())
                .filter(part -> part != null && !part.isBlank())
                .collect(Collectors.joining(SEPARATOR));
    }

    private boolean hasEnvironment() {
        return environment != null && !environment.isBlank();
    }

    static String icon(NotificationType type) {
        return switch (type) {
            case DAILY_SUMMARY -> "📅";
            case DELIVERY_FAILED -> "🚨";
            case TEST -> "🧪";
        };
    }

    static int color(NotificationSeverity severity) {
        return switch (severity) {
            case INFO -> 0x3B82F6;
            case SUCCESS -> 0x22C55E;
            case WARNING -> 0xF59E0B;
            case ERROR -> 0xEF4444;
        };
    }

    // ------------------------------ Description -----------------------------

    private String description(Notification notification) {
        StringBuilder description = new StringBuilder();

        if (notification.summary() != null && !notification.summary().isBlank()) {
            description.append(escape(notification.summary()));
        }

        List<NotificationEntry> entries = notification.entries();

        for (int i = 0; i < entries.size(); i++) {
            String entry = entry(entries.get(i));
            String separator = description.isEmpty() ? "" : "\n\n";

            if (description.length() + separator.length() + entry.length() > DESCRIPTION_MAX - OVERFLOW_RESERVE) {
                int remaining = entries.size() - i;
                description.append("\n\n… et ").append(remaining).append(remaining > 1 ? " autres" : " autre");
                break;
            }

            description.append(separator).append(entry);
        }

        return description.isEmpty() ? null : truncate(description.toString(), DESCRIPTION_MAX);
    }

    /**
     * <pre>
     * **&lt;t:…:t&gt;** · &lt;t:…:R&gt;
     * **[Marché de Noël](https://…)**
     * -# Facebook · Campagne Noël
     * </pre>
     */
    private static String entry(NotificationEntry entry) {
        List<String> lines = new ArrayList<>();

        if (entry.at() != null) {
            long epoch = entry.at().getEpochSecond();
            lines.add("**<t:" + epoch + ":t>**" + SEPARATOR + "<t:" + epoch + ":R>");
        }

        String label = escape(singleLine(entry.label()));
        lines.add(entry.link() == null ? "**" + label + "**" : "**[" + label + "](" + entry.link() + ")**");

        if (entry.detail() != null && !entry.detail().isBlank()) {
            lines.add("-# " + escape(singleLine(entry.detail())));
        }

        return String.join("\n", lines);
    }

    // --------------------------------- Champs -------------------------------

    private static List<DiscordWebhookPayload.Field> fields(List<NotificationField> fields) {
        return fields.stream()
                .limit(FIELDS_MAX)
                .map(field -> new DiscordWebhookPayload.Field(
                        truncate(escape(orDash(field.name())), FIELD_NAME_MAX),
                        field.code() ? codeBlock(field.value()) : truncate(escape(orDash(field.value())), FIELD_VALUE_MAX),
                        field.inline() ? Boolean.TRUE : null))
                .toList();
    }

    /** Les champs les moins importants, en fin de liste, sont sacrifiés au plafond global. */
    private static DiscordWebhookPayload.Embed withFields(DiscordWebhookPayload.Embed embed,
                                                          List<DiscordWebhookPayload.Field> fields) {
        List<DiscordWebhookPayload.Field> kept = new ArrayList<>(fields);

        while (!kept.isEmpty() && totalLength(embed, kept) > EMBED_TOTAL_MAX) {
            kept.removeLast();
        }

        return new DiscordWebhookPayload.Embed(embed.title(), embed.url(), embed.description(), embed.color(),
                kept.isEmpty() ? null : List.copyOf(kept), embed.footer(), embed.timestamp());
    }

    private static int totalLength(DiscordWebhookPayload.Embed embed, List<DiscordWebhookPayload.Field> fields) {
        int length = length(embed.title()) + length(embed.description()) + length(embed.footer().text());

        for (DiscordWebhookPayload.Field field : fields) {
            length += length(field.name()) + length(field.value());
        }

        return length;
    }

    /** Un message d'erreur brut, sans échappement : le bloc de code l'affiche tel quel. */
    private static String codeBlock(String value) {
        String raw = orDash(value).replace("```", "'''");
        int room = FIELD_VALUE_MAX - "```\n\n```".length();
        return "```\n" + truncate(raw, room) + "\n```";
    }

    // --------------------------------- Texte --------------------------------

    static String escape(String text) {
        if (text == null) {
            return null;
        }

        return MARKDOWN.matcher(text).replaceAll(match -> Matcher.quoteReplacement("\\" + match.group()));
    }

    private static String singleLine(String text) {
        return text == null ? "" : text.replaceAll("\\s*\\R\\s*", " ").strip();
    }

    private static String orDash(String text) {
        return text == null || text.isBlank() ? "—" : text;
    }

    static String truncate(String text, int max) {
        if (text == null || text.length() <= max) {
            return text;
        }

        String cut = text.substring(0, max - 1);

        // Ne pas laisser un échappement orphelin, qui avalerait le « … ».
        if (cut.endsWith("\\") && !cut.endsWith("\\\\")) {
            cut = cut.substring(0, cut.length() - 1);
        }

        return cut + "…";
    }

    private static int length(String text) {
        return text == null ? 0 : text.length();
    }
}
