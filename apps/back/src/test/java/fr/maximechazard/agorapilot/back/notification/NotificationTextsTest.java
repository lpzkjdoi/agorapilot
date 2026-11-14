package fr.maximechazard.agorapilot.back.notification;

import fr.maximechazard.agorapilot.back.publication.DeliveryChannel;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class NotificationTextsTest {

    @Test
    void designates_a_publication_by_its_first_non_blank_line() {
        assertThat(NotificationTexts.excerpt("\n\n  Marché de Noël samedi  \nDe 10h à 18h")).isEqualTo("Marché de Noël samedi");
    }

    @Test
    void shortens_a_long_first_line() {
        String excerpt = NotificationTexts.excerpt("a".repeat(200));

        assertThat(excerpt).hasSize(80).endsWith("…");
    }

    @Test
    void names_a_publication_without_text() {
        assertThat(NotificationTexts.excerpt("   ")).isEqualTo("(publication sans texte)");
        assertThat(NotificationTexts.excerpt(null)).isEqualTo("(publication sans texte)");
    }

    @Test
    void labels_channels_and_plurals() {
        assertThat(NotificationTexts.channelLabel(DeliveryChannel.FACEBOOK)).isEqualTo("Facebook");
        assertThat(NotificationTexts.plural(1, "diffusion", "diffusions")).isEqualTo("1 diffusion");
        assertThat(NotificationTexts.plural(3, "diffusion", "diffusions")).isEqualTo("3 diffusions");
    }
}
