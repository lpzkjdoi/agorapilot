package fr.maximechazard.agorapilot.back.publication;

/**
 * Cycle de vie d'une occurrence, déduit de celui de ses livraisons.
 * <p>
 * {@code SCHEDULED} signifie « au moins un canal reste à servir » : c'est à ce
 * titre que l'ordonnanceur retient une occurrence dont l'heure est passée. Une
 * occurrence n'en sort qu'une fois tous ses canaux tranchés, vers
 * {@code PUBLISHED} ou {@code FAILED} — sans quoi elle serait reprise à chaque
 * balayage, indéfiniment.
 */
public enum PublicationOccurrenceStatus {
    SCHEDULED,
    PUBLISHED,
    FAILED
}
