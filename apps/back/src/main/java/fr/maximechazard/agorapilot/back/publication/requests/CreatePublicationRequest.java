package fr.maximechazard.agorapilot.back.publication.requests;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
@SuppressWarnings("unused")
public class CreatePublicationRequest {

    @NotBlank
    @Size(max = 10000)
    private String content;
}
