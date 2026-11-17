package fr.maximechazard.agorapilot.back.publication.recurrence;

import fr.maximechazard.agorapilot.back.publication.exceptions.InvalidScheduleException;

import java.time.DayOfWeek;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.format.DateTimeFormatter;
import java.time.format.DateTimeParseException;
import java.time.temporal.TemporalAdjusters;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Comparator;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.TreeSet;
import java.util.function.Function;
import java.util.stream.Collectors;

/**
 * Règle de répétition d'une diffusion, au format {@code RRULE} de la RFC 5545
 * — celui des calendriers et d'{@code EKRecurrenceRule} (Rappels iOS/macOS).
 * <p>
 * Seul le sous-ensemble utile à la programmation est accepté :
 * <ul>
 *   <li>{@code FREQ} : {@code DAILY}, {@code WEEKLY}, {@code MONTHLY}, {@code YEARLY} — pas de répétition horaire ;</li>
 *   <li>{@code INTERVAL} : « tous les N » (1 par défaut) ;</li>
 *   <li>{@code BYDAY} : jours de la semaine ({@code MO,WE}), ou en mensuel/annuel un jour
 *   ordinal ({@code 1MO} le premier lundi, {@code -1FR} le dernier vendredi) ;</li>
 *   <li>{@code BYMONTHDAY} : jours du mois, 1 à 31 ;</li>
 *   <li>{@code BYMONTH} : mois de l'année, 1 à 12 ;</li>
 *   <li>{@code COUNT} ou {@code UNTIL} (date {@code AAAAMMJJ}) : fin de la répétition, l'un ou l'autre.</li>
 * </ul>
 * La date de départ ({@code DTSTART}) n'en fait pas partie : elle est portée à
 * côté, par la série.
 * <p>
 * <b>Écart volontaire à la norme</b> : un jour 29 à 31 absent du mois tombe le
 * dernier jour du mois (« le 31 » donne le 30 avril, le 28 ou 29 février), là
 * où la RFC le saute. Une annonce mensuelle « en fin de mois » ne doit pas
 * disparaître un mois sur deux.
 */
public record RecurrenceRule(
        Frequency frequency,
        int interval,
        List<WeekdayNum> byDay,
        List<Integer> byMonthDay,
        List<Integer> byMonth,
        Integer count,
        LocalDate until
) {

    /** Garde-fou : une règle qui ne produit rien ne doit pas boucler indéfiniment. */
    private static final int MAX_PERIODS = 10_000;

    private static final DateTimeFormatter UNTIL_FORMAT = DateTimeFormatter.BASIC_ISO_DATE;

    private static final Map<String, DayOfWeek> DAYS = Map.of(
            "MO", DayOfWeek.MONDAY, "TU", DayOfWeek.TUESDAY, "WE", DayOfWeek.WEDNESDAY,
            "TH", DayOfWeek.THURSDAY, "FR", DayOfWeek.FRIDAY, "SA", DayOfWeek.SATURDAY, "SU", DayOfWeek.SUNDAY);

    private static final Map<DayOfWeek, String> CODES = DAYS.entrySet().stream()
            .collect(Collectors.toMap(Map.Entry::getValue, Map.Entry::getKey));

    public enum Frequency { DAILY, WEEKLY, MONTHLY, YEARLY }

    /**
     * Un jour de la semaine, éventuellement ordinal : {@code ordinal} 0 = chaque
     * occurrence du jour, 1 = le premier du mois, -1 = le dernier.
     */
    public record WeekdayNum(int ordinal, DayOfWeek day) {

        @Override
        public String toString() {
            return (ordinal == 0 ? "" : String.valueOf(ordinal)) + CODES.get(day);
        }
    }

    public RecurrenceRule {
        // Forme normalisée : sans doublon, dans l'ordre de la semaine et du calendrier.
        byDay = byDay.stream().distinct()
                .sorted(Comparator.comparing(WeekdayNum::day).thenComparing(WeekdayNum::ordinal)).toList();
        byMonthDay = byMonthDay.stream().distinct().sorted().toList();
        byMonth = byMonth.stream().distinct().sorted().toList();
        validate(frequency, interval, byDay, byMonthDay, byMonth, count, until);
    }

    // ------------------------------------------------------------ lecture / écriture

    /**
     * Lit une règle {@code FREQ=WEEKLY;INTERVAL=2;BYDAY=MO} (le préfixe
     * {@code RRULE:} est toléré).
     *
     * @throws InvalidScheduleException règle illisible, ou hors du sous-ensemble accepté
     */
    public static RecurrenceRule parse(String rule) {
        if (rule == null || rule.isBlank()) {
            throw invalid("règle vide.");
        }

        String body = rule.strip();
        if (body.toUpperCase(Locale.ROOT).startsWith("RRULE:")) {
            body = body.substring("RRULE:".length());
        }

        Frequency frequency = null;
        int interval = 1;
        List<WeekdayNum> byDay = List.of();
        List<Integer> byMonthDay = List.of();
        List<Integer> byMonth = List.of();
        Integer count = null;
        LocalDate until = null;

        for (String part : body.split(";")) {
            String[] pair = part.split("=", 2);
            if (pair.length != 2 || pair[1].isBlank()) {
                throw invalid("élément « " + part + " » illisible.");
            }

            String value = pair[1].strip().toUpperCase(Locale.ROOT);
            switch (pair[0].strip().toUpperCase(Locale.ROOT)) {
                case "FREQ" -> frequency = parseFrequency(value);
                case "INTERVAL" -> interval = parseInt(value, "INTERVAL");
                case "BYDAY" -> byDay = list(value, RecurrenceRule::parseWeekdayNum);
                case "BYMONTHDAY" -> byMonthDay = list(value, v -> parseInt(v, "BYMONTHDAY"));
                case "BYMONTH" -> byMonth = list(value, v -> parseInt(v, "BYMONTH"));
                case "COUNT" -> count = parseInt(value, "COUNT");
                case "UNTIL" -> until = parseUntil(value);
                case "WKST" -> {
                    if (!value.equals("MO")) {
                        throw invalid("seules les semaines commençant le lundi (WKST=MO) sont prises en charge.");
                    }
                }
                default -> throw invalid("élément « " + pair[0] + " » non pris en charge.");
            }
        }

        if (frequency == null) {
            throw invalid("FREQ manquant.");
        }

        return new RecurrenceRule(frequency, interval, byDay, byMonthDay, byMonth, count, until);
    }

    /** La règle sous sa forme normalisée, sans préfixe {@code RRULE:}. */
    @Override
    public String toString() {
        StringBuilder rule = new StringBuilder("FREQ=").append(frequency);

        if (interval != 1) {
            rule.append(";INTERVAL=").append(interval);
        }
        if (!byDay.isEmpty()) {
            rule.append(";BYDAY=").append(byDay.stream().map(WeekdayNum::toString).collect(Collectors.joining(",")));
        }
        if (!byMonthDay.isEmpty()) {
            rule.append(";BYMONTHDAY=").append(byMonthDay.stream().map(String::valueOf).collect(Collectors.joining(",")));
        }
        if (!byMonth.isEmpty()) {
            rule.append(";BYMONTH=").append(byMonth.stream().map(String::valueOf).collect(Collectors.joining(",")));
        }
        if (count != null) {
            rule.append(";COUNT=").append(count);
        }
        if (until != null) {
            rule.append(";UNTIL=").append(until.format(UNTIL_FORMAT));
        }

        return rule.toString();
    }

    // ------------------------------------------------------------ calcul des dates

    /**
     * Les dates de la répétition à partir de {@code start} (inclus si la règle le
     * retient), jusqu'à {@code to} inclus, dans l'ordre. {@code COUNT} se compte
     * depuis {@code start} et {@code UNTIL} borne aussi la liste.
     */
    public List<LocalDate> dates(LocalDate start, LocalDate to) {
        LocalDate end = until != null && until.isBefore(to) ? until : to;
        List<LocalDate> dates = new ArrayList<>();

        for (int period = 0; period < MAX_PERIODS; period++) {
            LocalDate periodStart = periodStart(start, period);
            if (periodStart.isAfter(end)) {
                break;
            }

            for (LocalDate date : candidates(start, periodStart)) {
                if (date.isBefore(start)) {
                    continue;
                }
                if (date.isAfter(end) || (count != null && dates.size() >= count)) {
                    return dates;
                }
                dates.add(date);
            }
        }

        return dates;
    }

    /** Premier jour de la période numéro {@code period} (jour, semaine du lundi, mois, année). */
    private LocalDate periodStart(LocalDate start, int period) {
        long step = (long) period * interval;

        return switch (frequency) {
            case DAILY -> start.plusDays(step);
            case WEEKLY -> start.with(TemporalAdjusters.previousOrSame(DayOfWeek.MONDAY)).plusWeeks(step);
            case MONTHLY -> start.withDayOfMonth(1).plusMonths(step);
            case YEARLY -> start.withDayOfYear(1).plusYears(step);
        };
    }

    /** Les dates que la règle retient dans une période, triées. */
    private List<LocalDate> candidates(LocalDate start, LocalDate periodStart) {
        TreeSet<LocalDate> dates = new TreeSet<>();

        switch (frequency) {
            case DAILY -> {
                if (matchesFilters(periodStart)) {
                    dates.add(periodStart);
                }
            }
            case WEEKLY -> {
                List<DayOfWeek> days = byDay.isEmpty()
                        ? List.of(start.getDayOfWeek())
                        : byDay.stream().map(WeekdayNum::day).toList();
                for (DayOfWeek day : days) {
                    LocalDate date = periodStart.with(TemporalAdjusters.nextOrSame(day));
                    if (byMonth.isEmpty() || byMonth.contains(date.getMonthValue())) {
                        dates.add(date);
                    }
                }
            }
            case MONTHLY -> {
                if (byMonth.isEmpty() || byMonth.contains(periodStart.getMonthValue())) {
                    dates.addAll(inMonth(YearMonth.from(periodStart), start));
                }
            }
            case YEARLY -> {
                List<Integer> months = byMonth.isEmpty() ? List.of(start.getMonthValue()) : byMonth;
                for (int month : months) {
                    dates.addAll(inMonth(YearMonth.of(periodStart.getYear(), month), start));
                }
            }
        }

        return new ArrayList<>(dates);
    }

    /** En quotidien, {@code BYDAY} et {@code BYMONTH} filtrent les jours (« jours ouvrés »). */
    private boolean matchesFilters(LocalDate date) {
        boolean day = byDay.isEmpty() || byDay.stream().anyMatch(d -> d.day() == date.getDayOfWeek());
        boolean month = byMonth.isEmpty() || byMonth.contains(date.getMonthValue());
        boolean monthDay = byMonthDay.isEmpty() || byMonthDay.contains(date.getDayOfMonth());

        return day && month && monthDay;
    }

    /**
     * Les jours retenus dans un mois : jours du mois (un 31 absent tombe le
     * dernier jour), jours ordinaux (« le premier lundi »), ou à défaut le même
     * quantième que la date de départ.
     */
    private List<LocalDate> inMonth(YearMonth month, LocalDate start) {
        List<LocalDate> dates = new ArrayList<>();

        for (int day : byMonthDay) {
            dates.add(month.atDay(Math.min(day, month.lengthOfMonth())));
        }

        for (WeekdayNum weekday : byDay) {
            if (weekday.ordinal() == 0) {
                // Chaque lundi du mois.
                for (LocalDate date = month.atDay(1).with(TemporalAdjusters.nextOrSame(weekday.day()));
                     !date.isAfter(month.atEndOfMonth()); date = date.plusWeeks(1)) {
                    dates.add(date);
                }
            } else {
                LocalDate date = month.atDay(1).with(TemporalAdjusters.dayOfWeekInMonth(weekday.ordinal(), weekday.day()));
                // « Le 5e lundi » n'existe pas tous les mois : il est alors sauté.
                if (YearMonth.from(date).equals(month)) {
                    dates.add(date);
                }
            }
        }

        if (byMonthDay.isEmpty() && byDay.isEmpty()) {
            dates.add(month.atDay(Math.min(start.getDayOfMonth(), month.lengthOfMonth())));
        }

        return dates;
    }

    // ------------------------------------------------------------ validation

    private static void validate(Frequency frequency, int interval, List<WeekdayNum> byDay, List<Integer> byMonthDay,
                                 List<Integer> byMonth, Integer count, LocalDate until) {
        if (frequency == null) {
            throw invalid("FREQ manquant.");
        }
        if (interval < 1 || interval > 999) {
            throw invalid("INTERVAL doit être compris entre 1 et 999.");
        }
        if (count != null && until != null) {
            throw invalid("COUNT et UNTIL sont exclusifs : choisir une date de fin ou un nombre de diffusions.");
        }
        if (count != null && (count < 1 || count > 999)) {
            throw invalid("COUNT doit être compris entre 1 et 999.");
        }
        if (byMonthDay.stream().anyMatch(day -> day < 1 || day > 31)) {
            throw invalid("BYMONTHDAY accepte les jours 1 à 31.");
        }
        if (byMonth.stream().anyMatch(month -> month < 1 || month > 12)) {
            throw invalid("BYMONTH accepte les mois 1 à 12.");
        }
        boolean ordinal = byDay.stream().anyMatch(day -> day.ordinal() != 0);
        if (ordinal && (frequency == Frequency.DAILY || frequency == Frequency.WEEKLY)) {
            throw invalid("un jour ordinal (« 1MO ») n'a de sens qu'en répétition mensuelle ou annuelle.");
        }
        if (byDay.stream().anyMatch(day -> day.ordinal() < -5 || day.ordinal() > 5)) {
            throw invalid("le rang d'un jour (« 2MO ») va de -5 à 5.");
        }
        if (!byMonthDay.isEmpty() && frequency == Frequency.WEEKLY) {
            throw invalid("BYMONTHDAY n'a pas de sens en répétition hebdomadaire.");
        }
    }

    private static Frequency parseFrequency(String value) {
        try {
            return Frequency.valueOf(value);
        } catch (IllegalArgumentException e) {
            throw invalid("FREQ=" + value + " non pris en charge (DAILY, WEEKLY, MONTHLY ou YEARLY).");
        }
    }

    private static WeekdayNum parseWeekdayNum(String value) {
        if (value.length() < 2) {
            throw invalid("jour « " + value + " » illisible.");
        }

        DayOfWeek day = DAYS.get(value.substring(value.length() - 2));
        if (day == null) {
            throw invalid("jour « " + value + " » illisible.");
        }

        String ordinal = value.substring(0, value.length() - 2);
        int number = ordinal.isEmpty() ? 0 : parseInt(ordinal.startsWith("+") ? ordinal.substring(1) : ordinal, "BYDAY");
        if (!ordinal.isEmpty() && number == 0) {
            throw invalid("jour « " + value + " » illisible.");
        }

        return new WeekdayNum(number, day);
    }

    private static LocalDate parseUntil(String value) {
        try {
            // Une heure éventuelle (20261231T235959Z) est ignorée : la fin est un jour.
            return LocalDate.parse(value.length() > 8 ? value.substring(0, 8) : value, UNTIL_FORMAT);
        } catch (DateTimeParseException e) {
            throw invalid("UNTIL=" + value + " n'est pas une date AAAAMMJJ.");
        }
    }

    private static int parseInt(String value, String field) {
        try {
            return Integer.parseInt(value.strip());
        } catch (NumberFormatException e) {
            throw invalid(field + "=" + value + " n'est pas un nombre.");
        }
    }

    private static <T> List<T> list(String value, Function<String, T> parse) {
        return Arrays.stream(value.split(","))
                .map(String::strip)
                .filter(v -> !v.isEmpty())
                .map(parse)
                .toList();
    }

    private static InvalidScheduleException invalid(String reason) {
        return new InvalidScheduleException("Règle de répétition invalide : " + reason);
    }
}
