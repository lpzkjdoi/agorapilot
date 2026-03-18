package fr.maximechazard.agorapilot.back.publisher.facebook;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.Map;

@RestController
@RequestMapping("/admin/facebook")
@RequiredArgsConstructor
public class FacebookAdminController {

    private final FacebookTokenService tokenService;

    // Appeler une fois avec un token fraîchement généré depuis le Graph Explorer
    @PostMapping("/bootstrap")
    public ResponseEntity<String> bootstrap(@RequestParam String shortLivedToken) {
        tokenService.bootstrap(shortLivedToken);
        return ResponseEntity.ok("Token successfully initialized");
    }

    @GetMapping("/token/status")
    public ResponseEntity<Map<String, Object>> status() {
        return ResponseEntity.ok(Map.of("token", tokenService.getCurrentPageToken().substring(0, 10) + "..."));
    }
}