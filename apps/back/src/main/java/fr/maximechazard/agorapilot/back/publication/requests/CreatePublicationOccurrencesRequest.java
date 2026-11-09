package fr.maximechazard.agorapilot.back.publication.requests;

import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import jakarta.validation.Valid;
import jakarta.validation.constraints.AssertTrue;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.HashSet;
import java.util.List;
import java.util.Objects;
import java.util.Set;

/**
 * Programmation d'une publication à plusieurs dates d'un coup (le 11/11, le
 * 4/12, le 5/02…), sur les mêmes canaux. Chaque date garde sa forme : heure
 * automatique ou épinglée.
 */
@Getter
@SuppressWarnings("unused")
public class CreatePublicationOccurrencesRequest {
    /** Garde-fou contre une saisie emballée ; une année de diffusions hebdomadaires y tient. */
    public static final int MAX_SLOTS = 60;

    @NotNull
    private Long publicationId;

    @NotNull
    @NotEmpty
    private Set<DeliveryChannel> channels;

    @NotNull
    @NotEmpty
    @Size(max = MAX_SLOTS)
    private List<@Valid @NotNull OccurrenceSlotRequest> slots;

    /**
     * Deux fois le même jour automatique produirait deux posts identiques dans la
     * même soirée : c'est une erreur de saisie, pas une intention.
     */
    @AssertTrue(message = "Une même date figure deux fois dans la programmation.")
    public boolean isWithoutDuplicates() {
        if (slots == null) {
            return true;
        }

        Set<Object> seen = new HashSet<>();

        return slots.stream()
                .filter(Objects::nonNull)
                .map(OccurrenceSlotRequest::key)
                .filter(Objects::nonNull)
                .allMatch(seen::add);
    }
}
