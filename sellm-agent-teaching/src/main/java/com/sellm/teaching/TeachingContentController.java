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

    // ── 无状态草稿生成(不落库):提示词 / 教案正文 / 课件 ──

    @PostMapping("/draft/prompt")
    public Result<DraftText> draftPrompt(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                         @RequestBody GenerateContentRequest req) {
        requireUser(userId);
        return Result.ok(new DraftText(appService.genPromptDraft(req)));
    }

    @PostMapping("/draft/content")
    public Result<DraftText> draftContent(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                          @RequestBody GenerateContentRequest req) {
        requireUser(userId);
        return Result.ok(new DraftText(appService.genContentDraft(req)));
    }

    @PostMapping("/draft/courseware")
    public Result<DraftText> draftCourseware(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                             @RequestBody GenerateContentRequest req) {
        requireUser(userId);
        return Result.ok(new DraftText(appService.genCoursewareDraft(userId, req.getLessonId(), req.getSubjectNames())));
    }

    /** 定稿保存:一次性落库 FINALIZED(草稿态此前不入库)。 */
    @PostMapping("/finalize-new")
    public Result<ContentResponse> finalizeNew(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                               @RequestBody GenerateContentRequest req) {
        requireUser(userId);
        return Result.ok(ContentResponse.of(
            appService.finalizeNew(userId, req, req.getContent(), req.getSourceId())));
    }

    /** 草稿文本返回体。 */
    public record DraftText(String content) {}

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
