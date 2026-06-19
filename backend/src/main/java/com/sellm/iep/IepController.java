package com.sellm.iep;

import com.sellm.common.Result;
import com.sellm.iep.dto.GenerateIepRequest;
import com.sellm.iep.dto.IepFinalizeRequest;
import com.sellm.iep.dto.IepResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/ieps")
public class IepController {

    private final IepAppService appService;

    public IepController(IepAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public Result<IepResponse> generate(@RequestBody GenerateIepRequest req) {
        return Result.ok(toResponse(appService.generate(req.getReportId())));
    }

    @GetMapping("/{id}")
    public Result<IepResponse> get(@PathVariable Long id) {
        return Result.ok(toResponse(appService.get(id)));
    }

    @PutMapping("/{id}/finalize")
    public Result<IepResponse> finalizePlan(@PathVariable Long id, @RequestBody IepFinalizeRequest req) {
        return Result.ok(toResponse(appService.finalizePlan(id, req.getContent())));
    }

    private IepResponse toResponse(IepRecord r) {
        return new IepResponse(r.getId(), r.getDraft(), r.getFinalizedContent(), r.getStatus());
    }
}
