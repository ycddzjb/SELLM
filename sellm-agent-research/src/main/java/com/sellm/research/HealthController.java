package com.sellm.research;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {
    @GetMapping("/api/research/health")
    public Map<String, String> health() {
        return Map.of("service", "agent-research", "status", "UP");
    }
}
