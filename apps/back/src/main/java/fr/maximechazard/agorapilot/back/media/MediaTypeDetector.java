package fr.maximechazard.agorapilot.back.media;

import org.springframework.stereotype.Service;

import java.util.Optional;

/**
 * Reconnaît le format d'un fichier à partir de ses premiers octets.
 * <p>
 * Ni le {@code Content-Type} annoncé par le navigateur ni l'extension du nom de
 * fichier ne sont dignes de confiance : tous deux sont fournis par le client et
 * se falsifient en renommant le fichier. Seul le contenu fait foi.
 * <p>
 * Quatre formats à reconnaître : une dépendance de détection (Apache Tika) serait
 * disproportionnée.
 */
@Service
public class MediaTypeDetector {

    /** Nombre d'octets nécessaires pour trancher : « WEBP » se lit en 8..11. */
    public static final int HEADER_LENGTH = 12;

    private static final byte[] JPEG = {(byte) 0xFF, (byte) 0xD8, (byte) 0xFF};
    private static final byte[] PNG = {(byte) 0x89, 'P', 'N', 'G', 0x0D, 0x0A, 0x1A, 0x0A};
    private static final byte[] RIFF = {'R', 'I', 'F', 'F'};
    private static final byte[] WEBP = {'W', 'E', 'B', 'P'};
    private static final byte[] PDF = {'%', 'P', 'D', 'F', '-'};

    /**
     * @param header les premiers octets du fichier ({@value #HEADER_LENGTH} suffisent)
     * @return le format reconnu, ou {@link Optional#empty()} si aucun ne correspond
     */
    public Optional<MediaFileType> detect(byte[] header) {
        if (header == null) {
            return Optional.empty();
        }
        if (startsWith(header, 0, JPEG)) {
            return Optional.of(MediaFileType.JPEG);
        }
        if (startsWith(header, 0, PNG)) {
            return Optional.of(MediaFileType.PNG);
        }
        if (startsWith(header, 0, RIFF) && startsWith(header, 8, WEBP)) {
            return Optional.of(MediaFileType.WEBP);
        }
        if (startsWith(header, 0, PDF)) {
            return Optional.of(MediaFileType.PDF);
        }
        return Optional.empty();
    }

    private boolean startsWith(byte[] header, int offset, byte[] signature) {
        if (header.length < offset + signature.length) {
            return false;
        }
        for (int i = 0; i < signature.length; i++) {
            if (header[offset + i] != signature[i]) {
                return false;
            }
        }
        return true;
    }
}
