package fr.maximechazard.agorapilot.back.controller;

import fr.maximechazard.agorapilot.back.model.Publication;
import fr.maximechazard.agorapilot.back.service.PublicationService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/publications")
@RequiredArgsConstructor
@RestController
public class PublicationController {
    private final PublicationService publicationService;

    @GetMapping
    public ResponseEntity<Iterable<Publication>> getAll() {
        return new ResponseEntity<>(publicationService.getAll(), HttpStatus.OK);
    }
}
