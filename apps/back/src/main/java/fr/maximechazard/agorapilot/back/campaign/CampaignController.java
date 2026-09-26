package fr.maximechazard.agorapilot.back.campaign;

import fr.maximechazard.agorapilot.back.campaign.dtos.CampaignDTO;
import fr.maximechazard.agorapilot.back.campaign.requests.CreateCampaignRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RequestMapping("/api/campaigns")
@RequiredArgsConstructor
@RestController
public class CampaignController {
    private final CampaignService campaignService;

    // -------------------------------- GET --------------------------------
    @GetMapping
    public ResponseEntity<List<CampaignDTO>> getAll() {
        return new ResponseEntity<>(campaignService.getAll(), HttpStatus.OK);
    }

    // -------------------------------- POST --------------------------------
    @PostMapping
    public ResponseEntity<CampaignDTO> create(@Valid @RequestBody CreateCampaignRequest request) {
        return new ResponseEntity<>(campaignService.create(request), HttpStatus.CREATED);
    }

    /**
     * Clôture une campagne commencée : son statut passe à {@code COMPLETED} et
     * sa date de fin au présent. Répond {@code 409} si la campagne n'a pas
     * commencé ou si elle est déjà terminée.
     */
    @PostMapping("/{id}/closure")
    public ResponseEntity<CampaignDTO> close(@PathVariable Long id) {
        return new ResponseEntity<>(campaignService.close(id), HttpStatus.OK);
    }
}
