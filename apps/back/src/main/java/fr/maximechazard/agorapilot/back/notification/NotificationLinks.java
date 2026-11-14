package fr.maximechazard.agorapilot.back.notification;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Liens vers les écrans du front, repris dans les notifications. L'origine est
 * celle déjà déclarée pour CORS et le retour Facebook ({@code frontend.url}).
 */
@Component
public class NotificationLinks {

    private final String frontendUrl;

    public NotificationLinks(@Value("${frontend.url:}") String frontendUrl) {
        this.frontendUrl = frontendUrl == null ? "" : frontendUrl.replaceAll("/+$", "");
    }

    public String calendar() {
        return link("/calendrier");
    }

    public String publications() {
        return link("/publications");
    }

    /** Sans origine connue, pas de lien : Discord refuse une URL relative. */
    private String link(String path) {
        return frontendUrl.isEmpty() ? null : frontendUrl + path;
    }
}
