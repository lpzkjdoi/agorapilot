package fr.maximechazard.agorapilot.back.config;

import lombok.Getter;
import lombok.Setter;

/**
 * Corps d'erreur de l'API.
 * <p>
 * Les getters sont indispensables : sans eux Jackson ne sérialise aucun champ
 * et toutes les erreurs partaient en {@code {}}, message compris.
 */
@Getter
@Setter
public class ApiError {
    private String message;
    private int code;
}
