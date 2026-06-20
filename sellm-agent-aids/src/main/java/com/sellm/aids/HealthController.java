package com.sellm.aids;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {
    @GetMapping("/api/aids/health")
    public Map<String, String> health() {
        return Map.of("service", "agent-aids", "status", "UP");
    }
}
