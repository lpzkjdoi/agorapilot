package fr.maximechazard.agorapilot.back.publication.requests;

import jakarta.validation.constraints.Future;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.PastOrPresent;
import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@SuppressWarnings("unused")
public class CreatePublicationRequest {

    @NotBlank
    @Size(max = 10000)
    private String content;

    @Future
    private LocalDateTime scheduledAt;

    @PastOrPresent
    private LocalDateTime publishedAt;

    @Future
    private LocalDateTime endAt;
}
