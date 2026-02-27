package fr.maximechazard.agorapilot.back.controller;

import fr.maximechazard.agorapilot.back.model.Campaign;
import fr.maximechazard.agorapilot.back.service.CampaignService;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

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
}
