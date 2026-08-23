package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDTO;
import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDeliveryDTO;
import fr.maximechazard.agorapilot.back.publication.requests.CreatePublicationDeliveryRequest;
import fr.maximechazard.agorapilot.back.publication.requests.CreatePublicationRequest;
import fr.maximechazard.agorapilot.back.publication.requests.SetPublicationMediasRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/publications")
@RequiredArgsConstructor
@RestController
public class PublicationController {
    private final PublicationService publicationService;
    private final PublicationDeliveryService publicationDeliveryService;

    // -------------------------------- GET --------------------------------
    @GetMapping
    public ResponseEntity<Iterable<PublicationDTO>> getAll() {
        return new ResponseEntity<>(publicationService.getAll(), HttpStatus.OK);
    }

    // -------------------------------- POST --------------------------------
    @PostMapping
    public ResponseEntity<PublicationDTO> create(@RequestBody @Valid CreatePublicationRequest request) {
        return new ResponseEntity<>(publicationService.create(request), HttpStatus.CREATED);
    }

    /**
     * Diffuse immédiatement la publication sur le canal demandé, sans attendre
     * une occurrence planifiée. Une occurrence datée de l'instant présent est
     * créée pour tracer la diffusion.
     */
    @PostMapping("/{id}/deliveries")
    public ResponseEntity<PublicationDeliveryDTO> publishNow(
            @PathVariable Long id,
            @RequestBody @Valid CreatePublicationDeliveryRequest request
    ) {
        return new ResponseEntity<>(
                publicationDeliveryService.publishNow(id, request.getChannel()),
                HttpStatus.CREATED
        );
    }

    // -------------------------------- PUT --------------------------------

    /**
     * Remplace la liste ordonnée des visuels rattachés à la publication.
     * <p>
     * L'ordre du corps fait foi : le premier média sert de vignette et de
     * première photo à la diffusion.
     */
    @PutMapping("/{id}/medias")
    public ResponseEntity<PublicationDTO> setMedias(
            @PathVariable Long id,
            @RequestBody @Valid SetPublicationMediasRequest request
    ) {
        return new ResponseEntity<>(publicationService.setMedias(id, request.getMediaIds()), HttpStatus.OK);
    }
}
