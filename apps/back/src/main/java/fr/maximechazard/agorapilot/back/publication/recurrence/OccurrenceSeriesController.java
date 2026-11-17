package fr.maximechazard.agorapilot.back.publication.recurrence;

import fr.maximechazard.agorapilot.back.publication.recurrence.dtos.OccurrenceSeriesDTO;
import fr.maximechazard.agorapilot.back.publication.recurrence.requests.CreateOccurrenceSeriesRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/** Programmation par fréquence d'une publication. */
@RestController
@RequestMapping("/api/occurrence-series")
@RequiredArgsConstructor
public class OccurrenceSeriesController {

    private final OccurrenceSeriesService seriesService;

    @PostMapping
    public ResponseEntity<OccurrenceSeriesDTO> create(@Valid @RequestBody CreateOccurrenceSeriesRequest request) {
        return new ResponseEntity<>(seriesService.create(request), HttpStatus.CREATED);
    }

    @GetMapping
    public ResponseEntity<List<OccurrenceSeriesDTO>> findByPublication(@RequestParam Long publicationId) {
        return ResponseEntity.ok(seriesService.findByPublication(publicationId));
    }

    /** Arrête la série ; ses diffusions encore programmées sont annulées. */
    @DeleteMapping("/{id}")
    public ResponseEntity<OccurrenceSeriesDTO> stop(@PathVariable Long id) {
        return ResponseEntity.ok(seriesService.stop(id));
    }
}
