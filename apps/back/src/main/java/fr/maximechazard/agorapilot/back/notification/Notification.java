package fr.maximechazard.agorapilot.back.notification;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * Une notification, indépendante du canal qui la portera.
 * <p>
 * Tout est en texte brut : chaque {@link NotificationSender} fait sa propre mise
 * en forme (markdown et couleurs pour Discord, JSON tel quel pour un webhook
 * générique). Le code métier n'a donc jamais à connaître un canal.
 *
 * @param reference identifiant technique rappelé en pied (« occurrence #318 »), facultatif
 */
public record Notification(
        NotificationType type,
        NotificationSeverity severity,
        String title,
        String summary,
        String link,
        String reference,
        List<NotificationEntry> entries,
        List<NotificationField> fields,
        Instant occurredAt
) {
    public Notification {
        Objects.requireNonNull(type, "type");
        Objects.requireNonNull(severity, "severity");
        Objects.requireNonNull(title, "title");
        Objects.requireNonNull(occurredAt, "occurredAt");
        entries = entries == null ? List.of() : List.copyOf(entries);
        fields = fields == null ? List.of() : List.copyOf(fields);
    }

    public static Builder builder(NotificationType type, NotificationSeverity severity, String title) {
        return new Builder(type, severity, title);
    }

    public static final class Builder {
        private final NotificationType type;
        private final NotificationSeverity severity;
        private final String title;
        private String summary;
        private String link;
        private String reference;
        private final List<NotificationEntry> entries = new ArrayList<>();
        private final List<NotificationField> fields = new ArrayList<>();
        private Instant occurredAt;

        private Builder(NotificationType type, NotificationSeverity severity, String title) {
            this.type = type;
            this.severity = severity;
            this.title = title;
        }

        public Builder summary(String summary) {
            this.summary = summary;
            return this;
        }

        public Builder link(String link) {
            this.link = link;
            return this;
        }

        public Builder reference(String reference) {
            this.reference = reference;
            return this;
        }

        public Builder entry(NotificationEntry entry) {
            entries.add(entry);
            return this;
        }

        public Builder field(NotificationField field) {
            fields.add(field);
            return this;
        }

        public Builder occurredAt(Instant occurredAt) {
            this.occurredAt = occurredAt;
            return this;
        }

        public Notification build() {
            return new Notification(type, severity, title, summary, link, reference, entries, fields, occurredAt);
        }
    }
}
