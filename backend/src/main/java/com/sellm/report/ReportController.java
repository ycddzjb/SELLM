package com.sellm.report;

import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.common.Result;
import com.sellm.export.PdfExporter;
import com.sellm.report.dto.FinalizeRequest;
import com.sellm.report.dto.GenerateReportRequest;
import com.sellm.report.dto.ReportResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/reports")
public class ReportController {

    private final ReportAppService appService;
    private final PdfExporter pdfExporter;

    public ReportController(ReportAppService appService, PdfExporter pdfExporter) {
        this.appService = appService;
        this.pdfExporter = pdfExporter;
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

    @GetMapping
    public Result<List<ReportResponse>> listByChild(@RequestParam Long childId) {
        List<ReportResponse> out = new ArrayList<>();
        for (ReportRecord r : appService.listByChild(childId)) {
            out.add(toResponse(r));
        }
        return Result.ok(out);
    }

    @PutMapping("/{id}/finalize")
    public Result<ReportResponse> finalizeReport(@PathVariable Long id, @RequestBody FinalizeRequest req) {
        ReportRecord r = appService.finalizeReport(id, req.getContent());
        return Result.ok(toResponse(r));
    }

    /** 定稿后下载 PDF。行级权限经 appService.get 校验;未定稿 → 400。 */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        ReportRecord r = appService.get(id);
        if (!"FINALIZED".equals(r.getStatus()) || r.getFinalizedContent() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "请先定稿后再下载");
        }
        byte[] pdf = pdfExporter.toPdf("评估报告", r.getFinalizedContent());
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"report-" + id + ".pdf\"")
            .body(pdf);
    }

    private ReportResponse toResponse(ReportRecord r) {
        return new ReportResponse(r.getId(), r.getDraft(), r.getFinalizedContent(), r.getStatus());
    }
}
