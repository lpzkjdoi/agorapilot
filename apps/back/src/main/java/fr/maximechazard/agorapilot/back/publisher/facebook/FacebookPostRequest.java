package fr.maximechazard.agorapilot.back.publisher.facebook;

public record FacebookPostRequest(
        String message,
        String link,
        String url,
        String access_token
) {
}