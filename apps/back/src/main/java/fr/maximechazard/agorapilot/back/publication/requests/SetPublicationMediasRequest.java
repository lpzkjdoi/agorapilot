package fr.maximechazard.agorapilot.back.publication.requests;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;

/**
 * Remplace la liste des médias d'une publication.
 * <p>
 * L'ordre de la liste fait foi : le premier média sert de vignette et de
 * première photo à la diffusion. Une liste vide détache tout.
 */
@Getter
@SuppressWarnings("unused")
public class SetPublicationMediasRequest {

    @NotNull
    @Size(max = 20, message = "Une publication ne peut pas porter plus de 20 médias")
    private List<Long> mediaIds;
}
