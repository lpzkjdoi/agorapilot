package fr.maximechazard.agorapilot.back.publication.export;

import fr.maximechazard.agorapilot.back.publication.Publication;
import fr.maximechazard.agorapilot.back.publication.PublicationStatus;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotFoundException;
import fr.maximechazard.agorapilot.back.publication.exceptions.PublicationNotVerifiedException;
import fr.maximechazard.agorapilot.back.publication.repositories.PublicationRepository;
import org.junit.jupiter.api.Nested;
import org.junit.jupiter.api.Test;
import org.w3c.dom.Document;
import org.w3c.dom.Element;
import org.w3c.dom.NodeList;

import javax.xml.parsers.DocumentBuilderFactory;
import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

/**
 * L'export reprend le modèle EventProxy tel quel et n'y ajoute que la ligne de
 * la publication : on relit donc le classeur produit comme le ferait l'import.
 */
class EventProxyExportServiceTest {

    private static final Clock CLOCK = Clock.fixed(
            LocalDateTime.of(2026, 11, 17, 21, 0).atZone(ZoneId.systemDefault()).toInstant(), ZoneId.systemDefault());

    private final PublicationRepository repository = mock(PublicationRepository.class);
    private final EventProxyExportService service = new EventProxyExportService(repository, CLOCK, "La Buisse");

    private void given(String content, PublicationStatus status) {
        Publication publication = new Publication(content, status);
        publication.setId(7L);
        when(repository.findByIdAndArchivedFalse(7L)).thenReturn(Optional.of(publication));
    }

    private static Map<String, byte[]> unzip(byte[] xlsx) throws IOException {
        Map<String, byte[]> entries = new HashMap<>();
        try (ZipInputStream zip = new ZipInputStream(new ByteArrayInputStream(xlsx))) {
            for (ZipEntry entry; (entry = zip.getNextEntry()) != null; ) {
                entries.put(entry.getName(), zip.readAllBytes());
            }
        }
        return entries;
    }

    private static Document xml(byte[] content) throws Exception {
        return DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(new ByteArrayInputStream(content));
    }

    /** Cellules d'une ligne de la feuille principale, par référence (`A2` → texte). */
    private static Map<String, String> cells(byte[] xlsx, int row) throws Exception {
        Document sheet = xml(unzip(xlsx).get("xl/worksheets/sheet1.xml"));
        Map<String, String> cells = new LinkedHashMap<>();
        NodeList all = sheet.getElementsByTagName("c");
        for (int i = 0; i < all.getLength(); i++) {
            Element cell = (Element) all.item(i);
            if (cell.getAttribute("r").matches("[A-Z]+" + row)) {
                cells.put(cell.getAttribute("r"), cell.getTextContent());
            }
        }
        return cells;
    }

    @Test
    void fills_title_description_and_city_and_leaves_the_rest_empty() throws Exception {
        given("Marché de Noël samedi\nPlace de la mairie.\n\nVenez nombreux !", PublicationStatus.VERIFIED);

        EventProxyExport export = service.export(7L);

        assertThat(cells(export.content(), 2)).containsExactly(
                Map.entry("A2", "Marché de Noël samedi"),
                Map.entry("B2", "Place de la mairie.\n\nVenez nombreux !"),
                Map.entry("D2", "La Buisse"));
        assertThat(export.filename()).isEqualTo("EventProxy-2026-11-17-publication-7.xlsx");
    }

    @Test
    void keeps_the_template_headers_hidden_categories_and_category_drop_down() throws Exception {
        given("Conseil municipal jeudi", PublicationStatus.VERIFIED);

        Map<String, byte[]> entries = unzip(service.export(7L).content());
        Map<String, String> headers = cells(service.export(7L).content(), 1);
        Document sheet = xml(entries.get("xl/worksheets/sheet1.xml"));

        assertThat(headers).hasSize(20).containsEntry("A1", "Titre").containsEntry("D1", "Ville")
                .containsEntry("T1", "Identifiant image");
        assertThat(((Element) sheet.getElementsByTagName("dimension").item(0)).getAttribute("ref")).isEqualTo("A1:T2");
        assertThat(sheet.getElementsByTagName("dataValidation").item(0).getTextContent())
                .isEqualTo("Categories!$A$1:$A$76");
        assertThat(xml(entries.get("xl/worksheets/sheet2.xml")).getElementsByTagName("t").getLength()).isEqualTo(76);
        assertThat(new String(entries.get("xl/workbook.xml"))).contains("name=\"Categories\"", "state=\"hidden\"");
    }

    @Test
    void escapes_xml_and_drops_control_characters() throws Exception {
        given("Tarifs < 5 € & \"gratuit\" pour les <12 ans\u0007", PublicationStatus.VERIFIED);

        assertThat(cells(service.export(7L).content(), 2))
                .containsEntry("A2", "Tarifs < 5 € & \"gratuit\" pour les <12 ans");
    }

    @Test
    void refuses_a_draft() {
        given("Brouillon", PublicationStatus.DRAFT);

        assertThatThrownBy(() -> service.export(7L)).isInstanceOf(PublicationNotVerifiedException.class);
    }

    @Test
    void refuses_an_unknown_or_deleted_publication() {
        when(repository.findByIdAndArchivedFalse(7L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> service.export(7L)).isInstanceOf(PublicationNotFoundException.class);
    }

    @Nested
    class Headline {

        private EventProxyExportService.Headline of(String content) {
            return EventProxyExportService.Headline.of(content);
        }

        @Test
        void takes_the_first_non_blank_line_as_title() {
            assertThat(of("\n  Fermeture lundi  \r\nLa médiathèque sera fermée."))
                    .isEqualTo(new EventProxyExportService.Headline("Fermeture lundi", "La médiathèque sera fermée."));
        }

        @Test
        void cuts_a_single_block_after_a_short_first_sentence() {
            assertThat(of("Conseil municipal jeudi 20h. Ordre du jour en ligne."))
                    .isEqualTo(new EventProxyExportService.Headline("Conseil municipal jeudi 20h.", "Ordre du jour en ligne."));
        }

        @Test
        void keeps_a_single_sentence_whole_as_title() {
            assertThat(of("Atelier compostage au jardin partagé"))
                    .isEqualTo(new EventProxyExportService.Headline("Atelier compostage au jardin partagé", ""));
        }
    }
}
