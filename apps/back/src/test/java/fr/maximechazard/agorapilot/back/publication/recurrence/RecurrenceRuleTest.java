package fr.maximechazard.agorapilot.back.publication.recurrence;

import fr.maximechazard.agorapilot.back.publication.exceptions.InvalidScheduleException;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;

import java.time.LocalDate;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * Les préréglages de la modale (chaque jour, jours ouvrés, chaque semaine, toutes
 * les 2 semaines, chaque mois, tous les 3 mois, chaque année…) et la
 * personnalisation, exprimés en RRULE.
 */
class RecurrenceRuleTest {

    /** Un lundi. */
    private static final LocalDate MONDAY = LocalDate.of(2026, 11, 16);

    private static List<LocalDate> dates(String rule, LocalDate start, LocalDate to) {
        return RecurrenceRule.parse(rule).dates(start, to);
    }

    private static LocalDate d(int year, int month, int day) {
        return LocalDate.of(year, month, day);
    }

    @Nested
    class Presets {

        @Test
        void every_day() {
            assertThat(dates("FREQ=DAILY", MONDAY, MONDAY.plusDays(3)))
                    .containsExactly(d(2026, 11, 16), d(2026, 11, 17), d(2026, 11, 18), d(2026, 11, 19));
        }

        @Test
        void working_days() {
            assertThat(dates("FREQ=WEEKLY;BYDAY=MO,TU,WE,TH,FR", d(2026, 11, 20), d(2026, 11, 25)))
                    .containsExactly(d(2026, 11, 20), d(2026, 11, 23), d(2026, 11, 24), d(2026, 11, 25));
        }

        @Test
        void week_ends() {
            assertThat(dates("FREQ=WEEKLY;BYDAY=SA,SU", MONDAY, d(2026, 11, 29)))
                    .containsExactly(d(2026, 11, 21), d(2026, 11, 22), d(2026, 11, 28), d(2026, 11, 29));
        }

        @Test
        void every_week_on_the_start_day() {
            assertThat(dates("FREQ=WEEKLY", d(2026, 11, 18), d(2026, 12, 9)))
                    .containsExactly(d(2026, 11, 18), d(2026, 11, 25), d(2026, 12, 2), d(2026, 12, 9));
        }

        @Test
        void every_two_weeks() {
            assertThat(dates("FREQ=WEEKLY;INTERVAL=2", MONDAY, d(2026, 12, 31)))
                    .containsExactly(d(2026, 11, 16), d(2026, 11, 30), d(2026, 12, 14), d(2026, 12, 28));
        }

        @Test
        void every_month_on_the_start_day() {
            assertThat(dates("FREQ=MONTHLY", d(2026, 11, 5), d(2027, 2, 28)))
                    .containsExactly(d(2026, 11, 5), d(2026, 12, 5), d(2027, 1, 5), d(2027, 2, 5));
        }

        @Test
        void every_three_months() {
            assertThat(dates("FREQ=MONTHLY;INTERVAL=3", d(2026, 11, 5), d(2027, 12, 31)))
                    .containsExactly(d(2026, 11, 5), d(2027, 2, 5), d(2027, 5, 5), d(2027, 8, 5), d(2027, 11, 5));
        }

        @Test
        void every_year() {
            assertThat(dates("FREQ=YEARLY", d(2026, 12, 24), d(2029, 1, 1)))
                    .containsExactly(d(2026, 12, 24), d(2027, 12, 24), d(2028, 12, 24));
        }
    }

    @Nested
    class Custom {

        @Test
        void a_missing_29th_to_31st_falls_on_the_last_day_of_the_month() {
            assertThat(dates("FREQ=MONTHLY;BYMONTHDAY=31", d(2027, 1, 1), d(2027, 4, 30)))
                    .containsExactly(d(2027, 1, 31), d(2027, 2, 28), d(2027, 3, 31), d(2027, 4, 30));
            assertThat(dates("FREQ=MONTHLY", d(2028, 1, 30), d(2028, 3, 31)))
                    .containsExactly(d(2028, 1, 30), d(2028, 2, 29), d(2028, 3, 30));
        }

        @Test
        void several_days_of_the_month_in_calendar_order() {
            assertThat(dates("FREQ=MONTHLY;BYMONTHDAY=15,1", d(2026, 11, 10), d(2026, 12, 31)))
                    .containsExactly(d(2026, 11, 15), d(2026, 12, 1), d(2026, 12, 15));
        }

        @Test
        void the_first_monday_and_the_last_friday_of_the_month() {
            assertThat(dates("FREQ=MONTHLY;BYDAY=1MO", d(2026, 11, 1), d(2027, 1, 31)))
                    .containsExactly(d(2026, 11, 2), d(2026, 12, 7), d(2027, 1, 4));
            assertThat(dates("FREQ=MONTHLY;BYDAY=-1FR", d(2026, 11, 1), d(2026, 12, 31)))
                    .containsExactly(d(2026, 11, 27), d(2026, 12, 25));
        }

        @Test
        void a_fifth_monday_is_skipped_in_months_without_one() {
            assertThat(dates("FREQ=MONTHLY;BYDAY=5MO", d(2026, 11, 1), d(2027, 3, 31)))
                    .containsExactly(d(2026, 11, 30), d(2027, 3, 29));
        }

        @Test
        void chosen_months_of_the_year() {
            assertThat(dates("FREQ=YEARLY;BYMONTH=6,1;BYMONTHDAY=1", d(2026, 11, 1), d(2027, 12, 31)))
                    .containsExactly(d(2027, 1, 1), d(2027, 6, 1));
        }

        @Test
        void dates_before_the_start_are_left_out() {
            assertThat(dates("FREQ=WEEKLY;BYDAY=MO,FR", d(2026, 11, 18), d(2026, 11, 23)))
                    .containsExactly(d(2026, 11, 20), d(2026, 11, 23));
        }
    }

    @Nested
    class End {

        @Test
        void stops_after_count_occurrences_counted_from_the_start() {
            assertThat(dates("FREQ=WEEKLY;COUNT=3", MONDAY, d(2027, 12, 31)))
                    .containsExactly(d(2026, 11, 16), d(2026, 11, 23), d(2026, 11, 30));
        }

        @Test
        void stops_on_the_until_date_inclusive() {
            assertThat(dates("FREQ=WEEKLY;UNTIL=20261130", MONDAY, d(2027, 12, 31)))
                    .containsExactly(d(2026, 11, 16), d(2026, 11, 23), d(2026, 11, 30));
        }

        @Test
        void the_requested_bound_cuts_an_endless_rule() {
            assertThat(dates("FREQ=DAILY", MONDAY, MONDAY)).containsExactly(MONDAY);
        }
    }

    @Nested
    class Format {

        @Test
        void normalises_and_round_trips() {
            RecurrenceRule rule = RecurrenceRule.parse("RRULE:freq=monthly;byday=-1fr,1mo;interval=2;until=20271231T235959Z");

            assertThat(rule.toString()).isEqualTo("FREQ=MONTHLY;INTERVAL=2;BYDAY=1MO,-1FR;UNTIL=20271231");
            assertThat(RecurrenceRule.parse(rule.toString())).isEqualTo(rule);
        }

        @Test
        void sorts_month_days_numerically() {
            assertThat(RecurrenceRule.parse("FREQ=MONTHLY;BYMONTHDAY=10,2").toString())
                    .isEqualTo("FREQ=MONTHLY;BYMONTHDAY=2,10");
        }

        @ParameterizedTest
        @ValueSource(strings = {
                "", "INTERVAL=2", "FREQ=HOURLY", "FREQ=WEEKLY;INTERVAL=0", "FREQ=WEEKLY;COUNT=3;UNTIL=20271231",
                "FREQ=MONTHLY;BYMONTHDAY=32", "FREQ=YEARLY;BYMONTH=13", "FREQ=WEEKLY;BYDAY=1MO", "FREQ=WEEKLY;BYDAY=XX",
                "FREQ=MONTHLY;BYDAY=0MO", "FREQ=DAILY;BYSETPOS=1", "FREQ=WEEKLY;WKST=SU", "FREQ=WEEKLY;UNTIL=2027",
                "FREQ=WEEKLY;BYMONTHDAY=3"
        })
        void refuses_what_it_does_not_handle(String rule) {
            assertThatThrownBy(() -> RecurrenceRule.parse(rule))
                    .isInstanceOf(InvalidScheduleException.class)
                    .hasMessageStartingWith("Règle de répétition invalide");
        }
    }
}
