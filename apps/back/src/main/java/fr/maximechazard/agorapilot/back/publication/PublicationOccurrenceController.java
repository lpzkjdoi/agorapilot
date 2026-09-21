package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.publication.dtos.PublicationOccurrenceDTO;
import fr.maximechazard.agorapilot.back.publication.requests.CreatePublicationOccurrenceRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;
import java.util.Map;

@RequestMapping("/api/occurrences")
@RequiredArgsConstructor
@RestController
public class PublicationOccurrenceController {
    private final PublicationOccurrenceService publicationOccurrenceService;

    // -------------------------------- GET --------------------------------
    @GetMapping("/weekly")
    public ResponseEntity<Map<String, List<PublicationOccurrenceDTO>>> getWeekly() {
        return new ResponseEntity<>(publicationOccurrenceService.getWeeklyOccurrences(), HttpStatus.OK);
    }

    // -------------------------------- POST --------------------------------

    /**
     * Planifie la diffusion d'une publication sur un ou plusieurs canaux.
     * <p>
     * Rien n'est diffusé ici : l'occurrence naît {@code SCHEDULED} avec ses
     * livraisons en attente, et c'est {@code PublicationOccurrenceScheduler} qui
     * les sert à l'heure dite.
     */
    @PostMapping
    public ResponseEntity<PublicationOccurrenceDTO> create(@Valid @RequestBody CreatePublicationOccurrenceRequest request) {
        return new ResponseEntity<>(publicationOccurrenceService.create(request), HttpStatus.CREATED);
    }
}
