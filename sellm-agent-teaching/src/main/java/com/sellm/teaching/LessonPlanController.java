package com.sellm.teaching;

import com.sellm.common.Result;
import com.sellm.teaching.dto.*;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/teaching/lesson-plans")
public class LessonPlanController {

    private final TeachingAppService appService;
    public LessonPlanController(TeachingAppService appService) { this.appService = appService; }

    @PostMapping
    public Result<PlanResponse> generate(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                         @RequestBody GeneratePlanRequest req) {
        requireUser(userId);
        return Result.ok(appService.generatePlan(userId, req));
    }

    @PutMapping("/{id}")
    public Result<PlanResponse> edit(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                     @PathVariable("id") Long id, @RequestBody EditRequest req) {
        requireUser(userId);
        return Result.ok(appService.editPlan(userId, id, req));
    }

    @PostMapping("/{id}/finalize")
    public Result<PlanResponse> finalizePlan(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                             @PathVariable("id") Long id) {
        requireUser(userId);
        return Result.ok(appService.finalizePlan(userId, id));
    }

    @GetMapping("/{id}")
    public Result<PlanResponse> get(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                    @PathVariable("id") Long id) {
        requireUser(userId);
        return Result.ok(appService.getPlan(userId, id));
    }

    @GetMapping
    public Result<List<PlanResponse>> list(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUser(userId);
        return Result.ok(appService.listPlans(userId));
    }

    private void requireUser(Long userId) {
        if (userId == null) throw new UnauthorizedException("缺少 X-User-Id");
    }
}
