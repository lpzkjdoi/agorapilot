package fr.maximechazard.agorapilot.back.controller;

import fr.maximechazard.agorapilot.back.dto.CampaignDTO;
import fr.maximechazard.agorapilot.back.model.Campaign;
import fr.maximechazard.agorapilot.back.request.CreateCampaignRequest;
import fr.maximechazard.agorapilot.back.service.CampaignService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
@RestController
public class CampaignController {
    private final CampaignService campaignService;

    // -------------------------------- GET --------------------------------
    @GetMapping
    public ResponseEntity<Iterable<Campaign>> getAll() {
        return new ResponseEntity<>(campaignService.getAll(), HttpStatus.OK);
    }

    // -------------------------------- GET --------------------------------
    @PostMapping
    public ResponseEntity<CampaignDTO> create(@Valid @RequestBody CreateCampaignRequest request) {
        return new ResponseEntity<>(campaignService.create(request), HttpStatus.CREATED);
    }
}
