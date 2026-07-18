package fr.maximechazard.agorapilot.back.publisher.facebook;

import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.LinkedHashMap;
import java.util.Map;

@RestController
@RequestMapping("/admin/facebook")
@RequiredArgsConstructor
public class FacebookAdminController {

    private final FacebookTokenService tokenService;
    private final FacebookTokenScheduler tokenScheduler;

    // Appeler une fois avec un token fraîchement généré depuis le Graph Explorer
    @PostMapping("/bootstrap")
    public ResponseEntity<String> bootstrap(@RequestParam String shortLivedToken) {
        tokenService.bootstrap(shortLivedToken);
        return ResponseEntity.ok("Token successfully initialized");
    }

    @GetMapping("/token/status")
    public ResponseEntity<Map<String, Object>> status() {
        Map<String, Object> body = new LinkedHashMap<>();
        body.put("page", tokenService.getTokenStatus(FacebookTokenService.PAGE_TOKEN_TYPE).orElse(null));
        body.put("user", tokenService.getTokenStatus(FacebookTokenService.USER_TOKEN_TYPE).orElse(null));
        body.put("lastRenewalCheckAt", tokenScheduler.getLastCheckAt());
        body.put("lastRenewalError", tokenScheduler.getLastError());
        return ResponseEntity.ok(body);
    }
}
