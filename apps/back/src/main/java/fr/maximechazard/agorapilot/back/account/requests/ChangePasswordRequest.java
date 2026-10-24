package fr.maximechazard.agorapilot.back.account.requests;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

@Getter
@Setter
public class ChangePasswordRequest {
    @NotNull
    private String currentPassword;

    @NotNull
    private String newPassword;
}
