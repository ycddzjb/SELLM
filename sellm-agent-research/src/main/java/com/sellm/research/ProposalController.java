package com.sellm.research;

import com.sellm.agentcommon.UnauthorizedException;
import com.sellm.common.Result;
import com.sellm.research.dto.EditRequest;
import com.sellm.research.dto.GenerateProposalRequest;
import com.sellm.research.dto.ProposalResponse;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/research/proposals")
public class ProposalController {

    private final ProposalAppService appService;
    public ProposalController(ProposalAppService appService) { this.appService = appService; }

    @PostMapping
    public Result<ProposalResponse> generate(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                             @RequestBody GenerateProposalRequest req) {
        requireUser(userId);
        return Result.ok(appService.generate(userId, req));
    }

    @PutMapping("/{id}")
    public Result<ProposalResponse> edit(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                         @PathVariable("id") Long id, @RequestBody EditRequest req) {
        requireUser(userId);
        return Result.ok(appService.edit(userId, id, req));
    }

    @PostMapping("/{id}/finalize")
    public Result<ProposalResponse> finalizeProposal(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                                     @PathVariable("id") Long id) {
        requireUser(userId);
        return Result.ok(appService.finalizeProposal(userId, id));
    }

    @GetMapping("/{id}")
    public Result<ProposalResponse> get(@RequestHeader(value = "X-User-Id", required = false) Long userId,
                                        @PathVariable("id") Long id) {
        requireUser(userId);
        return Result.ok(appService.get(userId, id));
    }

    @GetMapping
    public Result<List<ResearchProposal>> mine(@RequestHeader(value = "X-User-Id", required = false) Long userId) {
        requireUser(userId);
        return Result.ok(appService.listMine(userId));
    }

    private void requireUser(Long userId) {
        if (userId == null) throw new UnauthorizedException("缺少 X-User-Id");
    }
}
