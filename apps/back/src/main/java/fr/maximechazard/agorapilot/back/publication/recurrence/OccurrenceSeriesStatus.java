package fr.maximechazard.agorapilot.back.publication.recurrence;

/**
 * {@code ACTIVE} : le job quotidien continue d'en créer les diffusions.
 * {@code COMPLETED} : la règle est arrivée à sa fin (date ou nombre de diffusions).
 * {@code STOPPED} : arrêtée à la main, ou avec la suppression de sa publication
 * ou la fin de sa campagne.
 */
public enum OccurrenceSeriesStatus {
    ACTIVE,
    COMPLETED,
    STOPPED
}
