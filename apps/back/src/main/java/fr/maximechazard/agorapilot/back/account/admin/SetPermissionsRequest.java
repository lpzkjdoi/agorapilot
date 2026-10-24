package fr.maximechazard.agorapilot.back.account.admin;

import jakarta.validation.constraints.NotNull;
import lombok.Getter;
import lombok.Setter;

import java.util.List;

/** Remplacement complet : les droits absents de la liste sont retirés. */
@Getter
@Setter
public class SetPermissionsRequest {
    @NotNull
    private List<String> permissions;
}
