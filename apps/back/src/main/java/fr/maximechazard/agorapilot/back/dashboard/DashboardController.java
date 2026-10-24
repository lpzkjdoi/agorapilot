package fr.maximechazard.agorapilot.back.dashboard;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RequestMapping("/api/dashboard")
@RequiredArgsConstructor
@RestController
public class DashboardController {
    private final DashboardService dashboardService;

    // -------------------------------- GET --------------------------------
    @GetMapping
    public ResponseEntity<DashboardDTO> getSummary() {
        return ResponseEntity.ok(dashboardService.getSummary());
    }
}
