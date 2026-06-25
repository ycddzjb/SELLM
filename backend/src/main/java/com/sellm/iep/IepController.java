package com.sellm.iep;

import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.common.Result;
import com.sellm.export.PdfExporter;
import com.sellm.iep.dto.GenerateIepRequest;
import com.sellm.iep.dto.IepFinalizeRequest;
import com.sellm.iep.dto.IepResponse;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

@RestController
@RequestMapping("/api/ieps")
public class IepController {

    private final IepAppService appService;
    private final PdfExporter pdfExporter;

    public IepController(IepAppService appService, PdfExporter pdfExporter) {
        this.appService = appService;
        this.pdfExporter = pdfExporter;
    }

    @PostMapping
    public Result<IepResponse> generate(@RequestBody GenerateIepRequest req) {
        return Result.ok(toResponse(appService.generate(req.getReportId(), req.getDiagnosisId())));
    }

    @GetMapping("/{id}")
    public Result<IepResponse> get(@PathVariable Long id) {
        return Result.ok(toResponse(appService.get(id)));
    }

    @GetMapping
    public Result<List<IepResponse>> listByChild(@RequestParam Long childId) {
        List<IepResponse> out = new ArrayList<>();
        for (IepRecord r : appService.listByChild(childId)) {
            out.add(toResponse(r));
        }
        return Result.ok(out);
    }

    @PutMapping("/{id}/finalize")
    public Result<IepResponse> finalizePlan(@PathVariable Long id, @RequestBody IepFinalizeRequest req) {
        return Result.ok(toResponse(appService.finalizePlan(id, req.getContent())));
    }

    /** 定稿后下载 PDF。行级权限经 appService.get 校验;未定稿 → 400。 */
    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        IepRecord r = appService.get(id);
        if (!"FINALIZED".equals(r.getStatus()) || r.getFinalizedContent() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "请先定稿后再下载");
        }
        byte[] pdf = pdfExporter.toPdf("IEP 计划", r.getFinalizedContent());
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"iep-" + id + ".pdf\"")
            .body(pdf);
    }

    private IepResponse toResponse(IepRecord r) {
        return new IepResponse(r.getId(), r.getDraft(), r.getFinalizedContent(), r.getStatus());
    }
}
