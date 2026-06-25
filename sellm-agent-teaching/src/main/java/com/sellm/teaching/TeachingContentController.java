package com.sellm.teaching;

import com.sellm.agentcommon.UnauthorizedException;
import com.sellm.common.Result;
import com.sellm.teaching.dto.ContentResponse;
import com.sellm.teaching.dto.EditRequest;
import com.sellm.teaching.dto.GenerateContentRequest;
import org.springframework.web.bind.annotation.*;

import java.util.List;

/** 教学模块内容(教案/课件/案例/习题统一)。X-User-Id 鉴权 + ownerId 行级权限。 */
@RestController
@RequestMapping("/api/teaching/contents")
public class TeachingContentController {

    private final TeachingContentAppService appService;
    public TeachingContentController(TeachingContentAppService appService) { this.appService = appService; }

    @PostMapping
    public Result<ContentResponse> generate(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                             @RequestBody GenerateContentRequest req) {
        requireUser(userId);
        return Result.ok(ContentResponse.of(appService.generate(userId, req)));
    }

    @PutMapping("/{id}")
    public Result<ContentResponse> edit(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                        @PathVariable("id") Long id, @RequestBody EditRequest req) {
        requireUser(userId);
        return Result.ok(ContentResponse.of(appService.edit(userId, id, req.getContent())));
    }

    @PostMapping("/{id}/finalize")
    public Result<ContentResponse> finalizeContent(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                                    @PathVariable("id") Long id) {
        requireUser(userId);
        return Result.ok(ContentResponse.of(appService.finalizeContent(userId, id)));
    }

    @GetMapping("/{id}")
    public Result<ContentResponse> get(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                       @PathVariable("id") Long id) {
        requireUser(userId);
        return Result.ok(ContentResponse.of(appService.get(userId, id)));
    }

    @GetMapping
    public Result<List<ContentResponse>> list(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                               @RequestParam("type") String type) {
        requireUser(userId);
        return Result.ok(appService.list(userId, type).stream().map(ContentResponse::of).toList());
    }

    private void requireUser(Long userId) {
        if (userId == null) throw new UnauthorizedException("缺少 X-User-Id");
    }
}
