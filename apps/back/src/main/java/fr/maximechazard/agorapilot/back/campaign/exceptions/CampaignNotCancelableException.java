package fr.maximechazard.agorapilot.back.campaign.exceptions;

import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.ResponseStatus;

/** La campagne existe, mais elle est déjà terminée ou déjà annulée. */
@ResponseStatus(HttpStatus.CONFLICT)
public class CampaignNotCancelableException extends RuntimeException {

    public CampaignNotCancelableException(String message) {
        super(message);
    }
}
