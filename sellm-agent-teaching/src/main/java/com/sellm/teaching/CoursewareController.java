package com.sellm.teaching;

import com.sellm.agentcommon.UnauthorizedException;
import com.sellm.common.Result;
import com.sellm.teaching.dto.*;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/teaching/courseware")
public class CoursewareController {

    private final TeachingAppService appService;
    public CoursewareController(TeachingAppService appService) { this.appService = appService; }

    @PostMapping
    public Result<CoursewareResponse> generate(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                               @RequestBody GenerateCoursewareRequest req) {
        requireUser(userId);
        return Result.ok(appService.generateCourseware(userId, req));
    }

    @PutMapping("/{id}")
    public Result<CoursewareResponse> edit(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                           @PathVariable("id") Long id, @RequestBody EditRequest req) {
        requireUser(userId);
        return Result.ok(appService.editCourseware(userId, id, req));
    }

    @PostMapping("/{id}/finalize")
    public Result<CoursewareResponse> finalizeCw(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                                 @PathVariable("id") Long id) {
        requireUser(userId);
        return Result.ok(appService.finalizeCourseware(userId, id));
    }

    @GetMapping("/{id}")
    public Result<CoursewareResponse> get(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                          @PathVariable("id") Long id) {
        requireUser(userId);
        return Result.ok(appService.getCourseware(userId, id));
    }

    private void requireUser(Long userId) {
        if (userId == null) throw new UnauthorizedException("缺少 X-User-Id");
    }
}
