package fr.maximechazard.agorapilot.back.campaign;

import fr.maximechazard.agorapilot.back.campaign.dtos.CampaignDTO;
import fr.maximechazard.agorapilot.back.campaign.requests.CreateCampaignRequest;
import fr.maximechazard.agorapilot.back.campaign.requests.UpdateCampaignRequest;
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

    @GetMapping("/{id}")
    public ResponseEntity<CampaignDTO> getById(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.getById(id));
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

    /**
     * Annule une campagne qui n'est pas terminée. Répond {@code 409} si elle est
     * déjà terminée ou annulée.
     */
    @PostMapping("/{id}/cancellation")
    public ResponseEntity<CampaignDTO> cancel(@PathVariable Long id) {
        return ResponseEntity.ok(campaignService.cancel(id));
    }

    // -------------------------------- PUT --------------------------------
    @PutMapping("/{id}")
    public ResponseEntity<CampaignDTO> update(@PathVariable Long id, @Valid @RequestBody UpdateCampaignRequest request) {
        return ResponseEntity.ok(campaignService.update(id, request));
    }

    // -------------------------------- DELETE --------------------------------

    /** Archive la campagne ; ses publications sont détachées, pas supprimées. */
    @DeleteMapping("/{id}")
    public ResponseEntity<Void> archive(@PathVariable Long id) {
        campaignService.archive(id);
        return ResponseEntity.noContent().build();
    }
}
