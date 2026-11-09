package fr.maximechazard.agorapilot.back.publication.requests;

import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.Future;
import lombok.Getter;

import java.time.LocalDate;
import java.time.LocalDateTime;

/**
 * Une date d'une programmation multiple, sous les deux mêmes formes exclusives
 * que {@link CreatePublicationOccurrenceRequest} : {@code date} seule (heure
 * automatique) ou {@code scheduledAt} (heure épinglée).
 */
@Getter
@SuppressWarnings("unused")
public class OccurrenceSlotRequest {
    private LocalDate date;

    /** Cf. {@link CreatePublicationOccurrenceRequest#getScheduledAt()} : jamais dans le passé. */
    @Future
    private LocalDateTime scheduledAt;

    @AssertTrue(message = "Renseigner soit date (heure automatique), soit scheduledAt (heure fixe), pas les deux.")
    public boolean isDateXorScheduledAt() {
        return (date == null) != (scheduledAt == null);
    }

    /** Ce qui rend deux dates identiques : le même jour automatique, ou la même heure épinglée. */
    Object key() {
        return date != null ? date : scheduledAt;
    }
}
