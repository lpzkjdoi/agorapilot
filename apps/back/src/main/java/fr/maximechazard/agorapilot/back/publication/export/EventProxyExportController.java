package fr.maximechazard.agorapilot.back.publication.export;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * Téléchargement d'une publication au format d'import EventProxy.
 * <p>
 * Un {@code GET} : l'export ne modifie rien, il se télécharge comme un fichier.
 */
@RestController
@RequestMapping("/api/publications")
@RequiredArgsConstructor
public class EventProxyExportController {

    static final MediaType XLSX =
            MediaType.parseMediaType("application/vnd.openxmlformats-officedocument.spreadsheetml.sheet");

    private final EventProxyExportService exportService;

    @GetMapping("/{id}/xlsx")
    public ResponseEntity<byte[]> export(@PathVariable Long id) {
        EventProxyExport export = exportService.export(id);

        return ResponseEntity.ok()
                .contentType(XLSX)
                .header(HttpHeaders.CONTENT_DISPOSITION, ContentDisposition.attachment()
                        .filename(export.filename())
                        .build()
                        .toString())
                .body(export.content());
    }
}
