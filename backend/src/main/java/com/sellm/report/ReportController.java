package com.sellm.report;

import com.sellm.common.Result;
import com.sellm.report.dto.FinalizeRequest;
import com.sellm.report.dto.GenerateReportRequest;
import com.sellm.report.dto.ReportResponse;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportAppService appService;

    public ReportController(ReportAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public Result<ReportResponse> generate(@RequestBody GenerateReportRequest req) {
        ReportRecord r = appService.generate(req.getAssessmentId());
        return Result.ok(toResponse(r));
    }

    @GetMapping("/{id}")
    public Result<ReportResponse> get(@PathVariable Long id) {
        ReportRecord r = appService.get(id);
        return Result.ok(toResponse(r));
    }

    @PutMapping("/{id}/finalize")
    public Result<ReportResponse> finalizeReport(@PathVariable Long id, @RequestBody FinalizeRequest req) {
        ReportRecord r = appService.finalizeReport(id, req.getContent());
        return Result.ok(toResponse(r));
    }

    private ReportResponse toResponse(ReportRecord r) {
        return new ReportResponse(r.getId(), r.getDraft(), r.getFinalizedContent(), r.getStatus());
    }
}
