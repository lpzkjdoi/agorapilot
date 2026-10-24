package fr.maximechazard.agorapilot.back.publication.requests;

import fr.maximechazard.agorapilot.back.publication.PublicationStatus;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;

/**
 * Corps de {@code PUT /api/publications/{id}}. Remplacement complet : le statut
 * est obligatoire, contrairement à la création où il vaut brouillon par défaut.
 */
@Getter
@SuppressWarnings("unused")
public class UpdatePublicationRequest {

    @NotBlank
    @Size(max = 10000)
    private String content;

    @NotNull
    private PublicationStatus status;
}
