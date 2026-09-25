package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.publication.dtos.PublicationOccurrenceDTO;
import fr.maximechazard.agorapilot.back.publication.requests.CreatePublicationOccurrenceRequest;
import fr.maximechazard.agorapilot.back.publication.requests.RescheduleOccurrenceRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;
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

    /** Occurrences de {@code [from, to)} — le calendrier du mois, typiquement. */
    @GetMapping
    public ResponseEntity<List<PublicationOccurrenceDTO>> findBetween(
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate from,
            @RequestParam @DateTimeFormat(iso = DateTimeFormat.ISO.DATE) LocalDate to) {
        return ResponseEntity.ok(publicationOccurrenceService.findBetween(from, to));
    }

    // -------------------------------- POST --------------------------------

    /**
     * Planifie la diffusion d'une publication sur un ou plusieurs canaux.
     * <p>
     * Rien n'est diffusé ici : l'occurrence naît {@code SCHEDULED} avec ses
     * livraisons en attente, et c'est {@code PublicationOccurrenceScheduler} qui
     * les sert à l'heure dite. Avec une {@code date} seule, cette heure est
     * choisie dans la fenêtre de publication du jour.
     */
    @PostMapping
    public ResponseEntity<PublicationOccurrenceDTO> create(@Valid @RequestBody CreatePublicationOccurrenceRequest request) {
        return new ResponseEntity<>(publicationOccurrenceService.create(request), HttpStatus.CREATED);
    }

    // -------------------------------- PUT --------------------------------

    /** Change le jour d'une diffusion, fixe son heure, ou la rend automatique. */
    @PutMapping("/{id}/schedule")
    public ResponseEntity<PublicationOccurrenceDTO> reschedule(@PathVariable Long id,
                                                               @Valid @RequestBody RescheduleOccurrenceRequest request) {
        return ResponseEntity.ok(publicationOccurrenceService.reschedule(id, request));
    }

    // -------------------------------- DELETE --------------------------------

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> cancel(@PathVariable Long id) {
        publicationOccurrenceService.cancel(id);
        return ResponseEntity.noContent().build();
    }
}
