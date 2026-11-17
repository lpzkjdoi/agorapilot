package fr.maximechazard.agorapilot.back.publication.export;

import fr.maximechazard.agorapilot.back.publication.Publication;
import fr.maximechazard.agorapilot.back.publication.PublicationStatus;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotFoundException;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotVerifiedException;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationRepository;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.core.io.ClassPathResource;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.UncheckedIOException;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.LocalDate;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;
import java.util.zip.ZipOutputStream;

/**
 * Export d'une publication au format d'import EventProxy : un classeur dont la
 * feuille « Sheet » porte une ligne d'en-têtes imposée (Titre, Description,
 * Ville…) et la feuille cachée « Categories » la liste des catégories admises.
 * <p>
 * Plutôt que de reconstruire ce classeur, le modèle fourni par l'outil est
 * repris tel quel ({@code export/eventproxy-modele.xlsx}) : en-têtes, feuille
 * cachée et liste déroulante de la colonne Catégorie restent ceux qu'attend
 * l'import. Seule la ligne 2 est ajoutée, en chaînes « inline » comme les
 * en-têtes du modèle — pas de table de chaînes partagées à tenir à jour, et
 * aucune bibliothèque tableur à embarquer.
 * <p>
 * Ne sont renseignés que le titre et la description, tirés du texte comme sur
 * la carte, et la ville, fixée par configuration. Les autres colonnes (dates
 * de l'événement, catégorie, image, liens, adresse…) n'ont pas d'équivalent
 * dans AgoraPilot : elles restent vides, à compléter dans l'outil.
 */
@Service
public class EventProxyExportService {

    static final String TEMPLATE = "export/eventproxy-modele.xlsx";
    private static final String SHEET = "xl/worksheets/sheet1.xml";
    private static final String HEADER_DIMENSION = "<dimension ref=\"A1:T1\"/>";
    private static final String END_OF_DATA = "</row></sheetData>";

    /** Un titre tiré d'un texte d'un seul bloc : sa première phrase, si elle est assez courte. */
    private static final Pattern FIRST_SENTENCE = Pattern.compile("^(.{8,120}?[.!?…])\\s+(\\S.*)$", Pattern.DOTALL);

    /** Caractères refusés par XML 1.0 (contrôles hors tabulation et sauts de ligne). */
    private static final Pattern INVALID_XML = Pattern.compile("[\\x00-\\x08\\x0B\\x0C\\x0E-\\x1F\\uFFFE\\uFFFF]");

    private final PublicationRepository publicationRepository;
    private final Clock clock;
    private final String city;

    public EventProxyExportService(PublicationRepository publicationRepository, Clock clock,
                                   @Value("${agorapilot.export.city:La Buisse}") String city) {
        this.publicationRepository = publicationRepository;
        this.clock = clock;
        this.city = city;
    }

    /**
     * @throws PublicationNotFoundException    la publication n'existe pas ou a été supprimée
     * @throws PublicationNotVerifiedException elle n'est pas vérifiée : comme pour toute diffusion,
     *                                         seul un texte relu part ailleurs
     */
    @Transactional(readOnly = true)
    public EventProxyExport export(Long publicationId) {
        Publication publication = publicationRepository.findByIdAndArchivedFalse(publicationId)
                .orElseThrow(() -> new PublicationNotFoundException(
                        "Publication with id " + publicationId + " does not exist."));

        if (publication.getStatus() != PublicationStatus.VERIFIED) {
            throw new PublicationNotVerifiedException(publicationId);
        }

        Headline headline = Headline.of(publication.getContent());
        Map<String, String> cells = new LinkedHashMap<>();
        cells.put("A", headline.title());
        cells.put("B", headline.description());
        cells.put("D", city);

        String filename = "EventProxy-" + LocalDate.now(clock) + "-publication-" + publicationId + ".xlsx";
        return new EventProxyExport(filename, fill(row(2, cells)));
    }

    /** Recopie le modèle en ajoutant la ligne donnée à la feuille principale. */
    private static byte[] fill(String row) {
        try (InputStream template = new ClassPathResource(TEMPLATE).getInputStream();
             ZipInputStream in = new ZipInputStream(template)) {
            ByteArrayOutputStream bytes = new ByteArrayOutputStream();

            try (ZipOutputStream out = new ZipOutputStream(bytes)) {
                for (ZipEntry entry; (entry = in.getNextEntry()) != null; ) {
                    byte[] content = in.readAllBytes();

                    if (SHEET.equals(entry.getName())) {
                        content = withRow(new String(content, StandardCharsets.UTF_8), row)
                                .getBytes(StandardCharsets.UTF_8);
                    }

                    out.putNextEntry(new ZipEntry(entry.getName()));
                    out.write(content);
                    out.closeEntry();
                }
            }

            return bytes.toByteArray();
        } catch (IOException e) {
            throw new UncheckedIOException("Modèle d'export EventProxy illisible (" + TEMPLATE + ").", e);
        }
    }

    private static String withRow(String sheet, String row) {
        // Le modèle n'a que sa ligne d'en-têtes : s'il change de forme, mieux vaut
        // une erreur franche qu'un fichier qui perdrait la ligne en silence.
        if (!sheet.contains(HEADER_DIMENSION) || !sheet.contains(END_OF_DATA)) {
            throw new IllegalStateException("Modèle d'export EventProxy inattendu : " + SHEET
                    + " devrait ne contenir que la ligne d'en-têtes A1:T1.");
        }

        return sheet.replace(HEADER_DIMENSION, "<dimension ref=\"A1:T2\"/>")
                .replace(END_OF_DATA, "</row>" + row + "</sheetData>");
    }

    /** Une ligne de cellules texte « inline » ; une valeur vide n'occupe pas de cellule. */
    static String row(int number, Map<String, String> cells) {
        StringBuilder row = new StringBuilder("<row r=\"").append(number).append("\">");

        cells.forEach((column, value) -> {
            if (value != null && !value.isBlank()) {
                row.append("<c r=\"").append(column).append(number).append("\" t=\"inlineStr\"><is>")
                        .append("<t xml:space=\"preserve\">").append(escape(value)).append("</t></is></c>");
            }
        });

        return row.append("</row>").toString();
    }

    static String escape(String value) {
        return INVALID_XML.matcher(value).replaceAll("")
                .replace("&", "&amp;")
                .replace("<", "&lt;")
                .replace(">", "&gt;")
                .replace("\"", "&quot;");
    }

    /**
     * Titre et description tirés du texte, selon la règle de la carte publication :
     * la première ligne non vide fait le titre, le reste la description (sauts de
     * ligne conservés). Un texte d'un seul bloc est coupé après sa première
     * phrase, si elle est assez courte pour un titre.
     */
    record Headline(String title, String description) {

        static Headline of(String content) {
            String text = content == null ? "" : content.strip();

            if (text.isEmpty()) {
                return new Headline("", "");
            }

            int lineEnd = text.indexOf('\n');
            if (lineEnd >= 0) {
                return new Headline(text.substring(0, lineEnd).strip(), text.substring(lineEnd + 1).strip());
            }

            Matcher sentence = FIRST_SENTENCE.matcher(text);
            return sentence.matches()
                    ? new Headline(sentence.group(1), sentence.group(2))
                    : new Headline(text, "");
        }
    }
}
