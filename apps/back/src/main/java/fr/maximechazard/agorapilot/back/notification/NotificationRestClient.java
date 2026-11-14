package fr.maximechazard.agorapilot.back.notification;

import org.springframework.web.client.RestClient;

/**
 * Le client HTTP des notifications, enveloppé dans son propre type : exposé
 * comme simple {@link RestClient}, il entrerait en concurrence avec celui de
 * Facebook, injecté par type.
 */
public record NotificationRestClient(RestClient client) {
}
