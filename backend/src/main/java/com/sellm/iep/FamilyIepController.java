package com.sellm.iep;

import com.sellm.common.Result;
import com.sellm.export.PdfExporter;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.iep.dto.FamilyIepResponse;
import com.sellm.iep.dto.GenerateFamilyIepRequest;
import com.sellm.iep.dto.IepFinalizeRequest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/** 家长家庭 IEP:家长设目标→大模型出家庭训练计划。行级权限在 AppService(家长仅自己孩子)。 */
@RestController
@RequestMapping("/api/family-ieps")
public class FamilyIepController {

    private final FamilyIepAppService appService;
    private final PdfExporter pdfExporter;

    public FamilyIepController(FamilyIepAppService appService, PdfExporter pdfExporter) {
        this.appService = appService;
        this.pdfExporter = pdfExporter;
    }

    @PostMapping
    public Result<FamilyIepResponse> generate(@RequestBody GenerateFamilyIepRequest req) {
        return Result.ok(FamilyIepResponse.of(appService.generate(req.getChildId(), req.getParentGoal())));
    }

    @GetMapping("/{id}")
    public Result<FamilyIepResponse> get(@PathVariable Long id) {
        return Result.ok(FamilyIepResponse.of(appService.get(id)));
    }

    @GetMapping
    public Result<List<FamilyIepResponse>> listByChild(@RequestParam Long childId) {
        List<FamilyIepResponse> out = new ArrayList<>();
        for (FamilyIep r : appService.listByChild(childId)) {
            out.add(FamilyIepResponse.of(r));
        }
        return Result.ok(out);
    }

    @PutMapping("/{id}/finalize")
    public Result<FamilyIepResponse> finalizePlan(@PathVariable Long id, @RequestBody IepFinalizeRequest req) {
        return Result.ok(FamilyIepResponse.of(appService.finalizePlan(id, req.getContent())));
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        FamilyIep r = appService.get(id);
        if (!"FINALIZED".equals(r.getStatus()) || r.getFinalizedContent() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "请先定稿后再下载");
        }
        byte[] pdf = pdfExporter.toPdf("家庭 IEP 计划", r.getFinalizedContent());
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"family-iep-" + id + ".pdf\"")
            .body(pdf);
    }
}
