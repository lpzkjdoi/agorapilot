package fr.maximechazard.agorapilot.back.publication.recurrence;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * Horizon de création des diffusions d'une série : le job quotidien les crée
 * jusqu'à {@code horizonDays} jours à l'avance, sans dépasser
 * {@code horizonCount} diffusions à venir par série. Les deux bornes valent
 * ensemble : une série quotidienne est retenue par le nombre, une série
 * annuelle par les jours.
 */
@ConfigurationProperties(prefix = "agorapilot.recurrence")
public record RecurrenceProperties(int horizonDays, int horizonCount) {

    public RecurrenceProperties {
        if (horizonDays < 1 || horizonCount < 1) {
            throw new IllegalArgumentException(
                    "agorapilot.recurrence.horizon-days et horizon-count doivent valoir au moins 1.");
        }
    }
}
