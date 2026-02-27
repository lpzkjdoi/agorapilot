package fr.maximechazard.agorapilot.back.controller;

import fr.maximechazard.agorapilot.back.model.Publication;
import fr.maximechazard.agorapilot.back.request.CreatePublicationRequest;
import fr.maximechazard.agorapilot.back.service.PublicationService;
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
    public ResponseEntity<Iterable<Publication>> getAll() {
        return new ResponseEntity<>(publicationService.getAll(), HttpStatus.OK);
    }

    // -------------------------------- POST --------------------------------
    @PostMapping
    public ResponseEntity<Publication> create(@RequestBody @Valid CreatePublicationRequest request) {
        return new ResponseEntity<>(publicationService.create(request), HttpStatus.CREATED);
    }
}
