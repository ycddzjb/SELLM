package com.sellm.qa;

import com.sellm.agentcommon.UnauthorizedException;
import com.sellm.common.Result;
import com.sellm.qa.dto.AskRequest;
import com.sellm.qa.dto.AskResponse;
import com.sellm.qa.dto.ConversationView;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

@RestController
@RequestMapping("/api/qa")
public class QaController {

    private final QaAppService appService;

    public QaController(QaAppService appService) { this.appService = appService; }

    @PostMapping("/ask")
    public Result<AskResponse> ask(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                   @RequestBody AskRequest req) {
        // userId 可空 = 匿名问答(网关白名单放行,不注入 X-User-Id);匿名不落库历史。
        return Result.ok(appService.ask(userId, req));
    }

    /** 多模态文档/图片分析(匿名可用)。multipart: file + 可选 question。 */
    @PostMapping("/analyze")
    public Result<String> analyze(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                  @RequestParam("file") MultipartFile file,
                                  @RequestParam(value = "question", required = false) String question) {
        try {
            String result = appService.analyzeDoc(
                file.getOriginalFilename(), file.getContentType(), file.getBytes(), question, null);
            return Result.ok(result);
        } catch (IOException e) {
            throw new com.sellm.common.BusinessException(com.sellm.common.ErrorCode.INVALID_INPUT, "文件读取失败");
        }
    }

    @GetMapping("/conversations")
    public Result<List<ConversationView>> myConversations(
            @RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUser(userId);
        return Result.ok(appService.listConversations(userId));
    }

    @GetMapping("/conversations/{id}")
    public Result<ConversationView> getConversation(
            @RequestHeader(value = "X-User-Id", required = false) Long userId,
            @PathVariable("id") Long id) {
        requireUser(userId);
        return Result.ok(appService.getConversation(userId, id));
    }

    private void requireUser(Long userId) {
        if (userId == null) {
            throw new UnauthorizedException("缺少 X-User-Id(网关未注入或直接访问)");
        }
    }
}
