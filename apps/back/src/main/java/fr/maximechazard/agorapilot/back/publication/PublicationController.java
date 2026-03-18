package fr.maximechazard.agorapilot.back.publication;

import fr.maximechazard.agorapilot.back.publication.dtos.PublicationDTO;
import fr.maximechazard.agorapilot.back.publication.requests.CreatePublicationRequest;
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
}
