package fr.maximechazard.agorapilot.back.media.requests;

import jakarta.validation.constraints.Size;
import lombok.Getter;

/**
 * Modification des métadonnées d'un média. Tous les champs sont facultatifs :
 * un champ absent ({@code null}) laisse la valeur existante inchangée.
 */
@Getter
@SuppressWarnings("unused")
public class UpdateMediaRequest {

    @Size(max = 255)
    private String title;

    @Size(max = 1000)
    private String altText;

    private Boolean archived;
}
