package fr.maximechazard.agorapilot.back.publication.exceptions;

/** Changer le statut d'une publication (vérifiée ou brouillon) exige {@code publications:verify}. */
public class VerificationNotAllowedException extends RuntimeException {
    public VerificationNotAllowedException() {
        super("Vous n'avez pas le droit de vérifier une publication, ni de la repasser en brouillon.");
    }
}
