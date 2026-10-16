package fr.maximechazard.agorapilot.back.publisher.suspension;

/**
 * Diffusion demandée sur un canal suspendu : refusée sans appeler le distant.
 */
public class ChannelSuspendedException extends RuntimeException {

    public ChannelSuspendedException(String message) {
        super(message);
    }
}
