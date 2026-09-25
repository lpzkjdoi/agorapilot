package fr.maximechazard.agorapilot.back.publication;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

/**
 * Répartit uniformément des diffusions dans une fenêtre horaire.
 * <p>
 * La fenêtre est découpée en autant de parts égales que de diffusions, et
 * chacune tombe au milieu de sa part : l'écart entre deux posts est ainsi le plus
 * grand possible, et aucune ne sort de la fenêtre quel que soit leur nombre.
 * Sur 15:00–22:00, une diffusion part à 18:30, deux à 16:45 et 20:15.
 * <p>
 * Les horaires sont arrondis aux 5 minutes, pour rester lisibles dans le
 * calendrier.
 */
public final class SlotPlanner {

    static final Duration GRANULARITY = Duration.ofMinutes(5);

    private SlotPlanner() {
    }

    /**
     * @param start début de la fenêtre, inclus
     * @param end   fin de la fenêtre, strictement après {@code start}
     * @param count nombre de diffusions à placer
     * @return {@code count} horaires croissants, compris dans {@code [start, end]}
     */
    public static List<LocalDateTime> plan(LocalDateTime start, LocalDateTime end, int count) {
        if (!start.isBefore(end)) {
            throw new IllegalArgumentException("Window start " + start + " must be before its end " + end + ".");
        }

        long span = Duration.between(start, end).toSeconds();
        List<LocalDateTime> slots = new ArrayList<>(count);

        for (int i = 0; i < count; i++) {
            long offset = Math.round((i + 0.5) * span / count);
            LocalDateTime slot = roundToGranularity(start.plusSeconds(offset));
            slots.add(clamp(slot, start, end));
        }

        return slots;
    }

    /** Arrondi au multiple de {@link #GRANULARITY} le plus proche. */
    static LocalDateTime roundToGranularity(LocalDateTime instant) {
        long step = GRANULARITY.toSeconds();
        long secondOfDay = instant.toLocalTime().toSecondOfDay();
        long rounded = Math.round((double) secondOfDay / step) * step;
        return instant.toLocalDate().atStartOfDay().plusSeconds(rounded);
    }

    /** Arrondi au multiple de {@link #GRANULARITY} supérieur ou égal. */
    static LocalDateTime ceilToGranularity(LocalDateTime instant) {
        long step = GRANULARITY.toSeconds();
        long secondOfDay = instant.toLocalTime().toSecondOfDay();
        long ceiled = (secondOfDay + step - 1) / step * step;
        return instant.toLocalDate().atStartOfDay().plusSeconds(ceiled);
    }

    private static LocalDateTime clamp(LocalDateTime slot, LocalDateTime start, LocalDateTime end) {
        if (slot.isBefore(start)) {
            return start;
        }
        return slot.isAfter(end) ? end : slot;
    }
}
