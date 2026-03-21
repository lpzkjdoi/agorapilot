package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.publication.dtos.PublicationOccurrenceDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
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
}
