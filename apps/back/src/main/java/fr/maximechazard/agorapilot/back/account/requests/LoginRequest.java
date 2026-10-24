package fr.maximechazard.agorapilot.back.account.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

/**
 * Bornes larges et seulement contre les abus : une saisie hors règle doit
 * recevoir la même réponse qu'un mauvais mot de passe, pas un 400 plus bavard.
 */
@Getter
@Setter
public class LoginRequest {
    @NotNull
    @Size(max = 256)
    private String username;

    @NotNull
    @Size(max = 256)
    private String password;
}
