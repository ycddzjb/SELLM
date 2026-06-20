package com.sellm.teaching;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Map;

@RestController
public class HealthController {
    @GetMapping("/api/teaching/health")
    public Map<String, String> health() {
        return Map.of("service", "agent-teaching", "status", "UP");
    }
}
