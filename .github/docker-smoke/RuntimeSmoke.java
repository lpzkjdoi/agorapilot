import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.net.URI;
import java.net.http.HttpClient;
import java.net.http.HttpRequest;
import java.net.http.HttpResponse;
import java.time.Duration;
import java.time.LocalDate;
import java.time.ZoneId;
import java.time.format.DateTimeFormatter;
import java.util.Locale;
import javax.imageio.ImageIO;
import javax.net.ssl.SSLContext;

/**
 * Vérifie, dans l'image back construite, ce que le JRE minimal (jlink) pourrait
 * avoir perdu et qu'un simple démarrage de Spring ne montre pas : traitement des
 * images en headless, locale française, fuseau, encodage, TLS sortant.
 *
 * Compilé sur le runner (`javac --release 21`), puis lancé avec le `java` de
 * l'image : le runtime n'embarque pas de compilateur. Utilisé par
 * .github/workflows/docker-ci.yml.
 */
public class RuntimeSmoke {

    public static void main(String[] args) throws Exception {
        images();
        locale();
        timezone();
        encoding();
        tls();
        System.out.println("RuntimeSmoke : tout est en ordre.");
    }

    /** Ce que fait PublishableImageFactory : aplatir sur fond blanc, ré-encoder en JPEG. */
    private static void images() throws Exception {
        BufferedImage source = new BufferedImage(64, 48, BufferedImage.TYPE_INT_ARGB);
        BufferedImage opaque = new BufferedImage(64, 48, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = opaque.createGraphics();
        try {
            graphics.setColor(Color.WHITE);
            graphics.fillRect(0, 0, 64, 48);
            graphics.drawImage(source, 0, 0, null);
        } finally {
            graphics.dispose();
        }
        ByteArrayOutputStream out = new ByteArrayOutputStream();
        check(ImageIO.write(opaque, "jpg", out), "aucun encodeur JPEG");
        BufferedImage back = ImageIO.read(new ByteArrayInputStream(out.toByteArray()));
        check(back != null && back.getWidth() == 64, "JPEG relu incorrect");
        System.out.println("Images : JPEG de " + out.size() + " octets.");
    }

    private static void locale() {
        String month = LocalDate.of(2026, 11, 1).format(DateTimeFormatter.ofPattern("MMMM", Locale.FRENCH));
        check("novembre".equals(month), "locale française absente (mois : " + month + ")");
        System.out.println("Locale : " + month + ".");
    }

    private static void timezone() {
        ZoneId zone = ZoneId.systemDefault();
        check("Europe/Paris".equals(zone.getId()), "fuseau inattendu : " + zone);
        System.out.println("Fuseau : " + zone + ".");
    }

    private static void encoding() {
        String jnu = System.getProperty("sun.jnu.encoding");
        check("UTF-8".equalsIgnoreCase(jnu), "encodage des noms de fichiers : " + jnu);
        System.out.println("Encodage : " + jnu + ".");
    }

    /** Le back appelle graph.facebook.com et Discord en HTTPS. */
    private static void tls() throws Exception {
        HttpClient client = HttpClient.newBuilder()
                .sslContext(SSLContext.getDefault())
                .connectTimeout(Duration.ofSeconds(10))
                .build();
        HttpResponse<Void> response = client.send(
                HttpRequest.newBuilder(URI.create("https://graph.facebook.com/")).GET().build(),
                HttpResponse.BodyHandlers.discarding());
        System.out.println("TLS : graph.facebook.com a répondu " + response.statusCode()
                + " en " + response.sslSession().map(s -> s.getProtocol() + " / " + s.getCipherSuite()).orElse("?") + ".");
    }

    private static void check(boolean condition, String message) {
        if (!condition) {
            throw new IllegalStateException(message);
        }
    }
}
