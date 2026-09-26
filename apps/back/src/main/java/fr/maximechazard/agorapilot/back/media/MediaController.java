package fr.maximechazard.agorapilot.back.media;

import fr.maximechazard.agorapilot.back.media.dtos.MediaContent;
import fr.maximechazard.agorapilot.back.media.dtos.MediaDTO;
import fr.maximechazard.agorapilot.back.media.requests.UpdateMediaRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.Resource;
import org.springframework.http.*;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.List;

@RequestMapping("/api/medias")
@RequiredArgsConstructor
@RestController
public class MediaController {
    private final MediaService mediaService;

    // -------------------------------- GET --------------------------------

    /**
     * @param archived {@code false} pour la médiathèque courante, {@code true} pour
     *                 les seuls médias archivés, absent pour tout obtenir
     */
    @GetMapping
    public ResponseEntity<List<MediaDTO>> getAll(@RequestParam(required = false) Boolean archived) {
        return new ResponseEntity<>(mediaService.getAll(archived), HttpStatus.OK);
    }

    @GetMapping("/{id}")
    public ResponseEntity<MediaDTO> getOne(@PathVariable Long id) {
        return new ResponseEntity<>(mediaService.get(id), HttpStatus.OK);
    }

    /**
     * Sert le binaire.
     * <p>
     * Le contenu est immuable — un média modifié est un média différent — d'où un
     * cache d'un an et un {@code ETag} égal au SHA-256, qui permet au navigateur de
     * revalider sans retélécharger.
     * <p>
     * {@code nosniff} est indispensable : sans lui, un navigateur peut réinterpréter
     * le contenu et exécuter comme du HTML un fichier annoncé comme image.
     */
    @GetMapping("/{id}/file")
    public ResponseEntity<Resource> download(@PathVariable Long id) {
        MediaContent content = mediaService.loadContent(id);
        MediaDTO metadata = content.metadata();

        ContentDisposition disposition = ContentDisposition.inline()
                .filename(metadata.originalFilename(), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(MediaType.parseMediaType(metadata.contentType()))
                .contentLength(metadata.sizeBytes())
                .eTag("\"" + metadata.checksum() + "\"")
                .cacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
                .header(HttpHeaders.CONTENT_DISPOSITION, disposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(content.resource());
    }

    // -------------------------------- POST -------------------------------

    @PostMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ResponseEntity<MediaDTO> upload(
            @RequestPart("file") MultipartFile file,
            @RequestParam(required = false) String title,
            @RequestParam(required = false) String altText
    ) {
        return new ResponseEntity<>(mediaService.upload(file, title, altText), HttpStatus.CREATED);
    }

    // -------------------------------- PATCH ------------------------------

    @PatchMapping("/{id}")
    public ResponseEntity<MediaDTO> update(@PathVariable Long id, @Valid @RequestBody UpdateMediaRequest request) {
        return new ResponseEntity<>(mediaService.update(id, request), HttpStatus.OK);
    }

    // ------------------------------- DELETE ------------------------------

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> delete(@PathVariable Long id) {
        mediaService.delete(id);
        return ResponseEntity.noContent().build();
    }
}
