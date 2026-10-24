package fr.maximechazard.agorapilot.back.account.admin;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

@Getter
@Setter
public class CreateAccountRequest {
    @NotNull
    @Size(max = 64)
    private String username;

    /** Codes des droits ({@code publications:read}…) ; vide : aucun droit. */
    @NotNull
    private List<String> permissions;
}
