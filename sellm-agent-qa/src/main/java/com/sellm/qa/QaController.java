package com.sellm.qa;

import com.sellm.agentcommon.UnauthorizedException;
import com.sellm.common.Result;
import com.sellm.qa.dto.AskRequest;
import com.sellm.qa.dto.AskResponse;
import com.sellm.qa.dto.ConversationView;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/qa")
public class QaController {

    private final QaAppService appService;

    public QaController(QaAppService appService) { this.appService = appService; }

    @PostMapping("/ask")
    public Result<AskResponse> ask(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                   @RequestBody AskRequest req) {
        requireUser(userId);
        return Result.ok(appService.ask(userId, req));
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
