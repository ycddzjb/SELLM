package com.sellm.research;

import com.sellm.agentcommon.UnauthorizedException;
import com.sellm.common.Result;
import com.sellm.research.dto.ReliabilityRequest;
import com.sellm.research.dto.ReliabilityResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/research/reliability")
public class ReliabilityController {

    private final ReliabilityAppService appService;
    public ReliabilityController(ReliabilityAppService appService) { this.appService = appService; }

    @PostMapping
    public Result<ReliabilityResponse> compute(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                               @RequestBody ReliabilityRequest req) {
        requireUser(userId);
        return Result.ok(appService.compute(userId, req));
    }

    @GetMapping("/{id}")
    public Result<ReliabilityResponse> get(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                           @PathVariable("id") Long id) {
        requireUser(userId);
        return Result.ok(appService.get(userId, id));
    }

    @GetMapping
    public Result<List<ReliabilityCalc>> mine(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUser(userId);
        return Result.ok(appService.listMine(userId));
    }

    private void requireUser(Long userId) {
        if (userId == null) throw new UnauthorizedException("缺少 X-User-Id");
    }
}
