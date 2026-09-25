package fr.maximechazard.agorapilot.back.publication;

import org.junit.jupiter.api.Test;

import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.LocalTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

class SlotPlannerTest {

    private static final LocalDate DAY = LocalDate.of(2026, 10, 3);
    private static final LocalDateTime START = DAY.atTime(15, 0);
    private static final LocalDateTime END = DAY.atTime(22, 0);

    private static List<LocalTime> times(int count) {
        return SlotPlanner.plan(START, END, count).stream().map(LocalDateTime::toLocalTime).toList();
    }

    @Test
    void places_nothing_when_there_is_nothing_to_place() {
        assertThat(SlotPlanner.plan(START, END, 0)).isEmpty();
    }

    @Test
    void places_a_single_publication_in_the_middle_of_the_window() {
        assertThat(times(1)).containsExactly(LocalTime.of(18, 30));
    }

    @Test
    void spreads_publications_at_the_centre_of_equal_shares() {
        assertThat(times(2)).containsExactly(LocalTime.of(16, 45), LocalTime.of(20, 15));
        assertThat(times(3)).containsExactly(LocalTime.of(16, 10), LocalTime.of(18, 30), LocalTime.of(20, 50));
        // Parts de 84 min, centres à 15:42, 17:06… arrondis aux 5 minutes.
        assertThat(times(5)).containsExactly(
                LocalTime.of(15, 40), LocalTime.of(17, 5), LocalTime.of(18, 30),
                LocalTime.of(19, 55), LocalTime.of(21, 20));
    }

    @Test
    void keeps_ten_publications_inside_the_window_evenly_spaced() {
        List<LocalTime> slots = times(10);

        assertThat(slots).hasSize(10).isSorted();
        assertThat(slots.getFirst()).isEqualTo(LocalTime.of(15, 20));
        assertThat(slots.getLast()).isEqualTo(LocalTime.of(21, 40));
    }

    /** Pas de limite : l'écart se resserre, sans jamais sortir de la fenêtre. */
    @Test
    void never_leaves_the_window_however_many_publications() {
        List<LocalDateTime> slots = SlotPlanner.plan(START, END, 200);

        assertThat(slots).hasSize(200).isSorted()
                .allSatisfy(slot -> assertThat(slot).isBetween(START, END));
    }

    @Test
    void only_produces_five_minute_times() {
        assertThat(SlotPlanner.plan(START, END, 7))
                .allSatisfy(slot -> {
                    assertThat(slot.getMinute() % 5).isZero();
                    assertThat(slot.getSecond()).isZero();
                });
    }

    @Test
    void works_on_a_partial_window() {
        // Aujourd'hui, programmé à 18:32 : il reste 18:40–22:00.
        assertThat(SlotPlanner.plan(DAY.atTime(18, 40), END, 2).stream().map(LocalDateTime::toLocalTime))
                .containsExactly(LocalTime.of(19, 30), LocalTime.of(21, 10));
    }

    @Test
    void rejects_an_empty_window() {
        assertThatThrownBy(() -> SlotPlanner.plan(END, END, 1)).isInstanceOf(IllegalArgumentException.class);
    }

    @Test
    void rounds_up_to_the_next_five_minutes() {
        assertThat(SlotPlanner.ceilToGranularity(DAY.atTime(18, 32, 10))).isEqualTo(DAY.atTime(18, 35));
        assertThat(SlotPlanner.ceilToGranularity(DAY.atTime(18, 35))).isEqualTo(DAY.atTime(18, 35));
    }
}
