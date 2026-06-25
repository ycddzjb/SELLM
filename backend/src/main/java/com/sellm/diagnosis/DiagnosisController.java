package com.sellm.diagnosis;

import com.sellm.common.Result;
import com.sellm.diagnosis.dto.DiagnosisResponse;
import com.sellm.diagnosis.dto.GenerateDiagnosisRequest;
import com.sellm.export.PdfExporter;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;

/**
 * 多模态诊断:建诊断 → 挂多模态素材(识别)→ 生成结构化诊断 → 编辑 → 定稿 → PDF。
 * 端点级 RBAC 由 SecurityConfig 控制(POST/PUT 限 TEACHER/MANAGER);行级 AccessGuard。
 * AI 只产 DRAFT,人工定稿;PDF 仅 FINALIZED。
 */
@RestController
@RequestMapping("/api/diagnoses")
public class DiagnosisController {

    private final DiagnosisAppService appService;
    private final PdfExporter pdfExporter;

    public DiagnosisController(DiagnosisAppService appService, PdfExporter pdfExporter) {
        this.appService = appService;
        this.pdfExporter = pdfExporter;
    }

    /** 建诊断(DRAFT,空内容),返回 id 供挂素材。 */
    @PostMapping
    public Result<DiagnosisResponse> create(@RequestBody GenerateDiagnosisRequest req) {
        return Result.ok(DiagnosisResponse.of(
            appService.create(req.getChildId(), req.getScaleId(), req.getStructuredInput())));
    }

    /** 挂多模态素材并识别(file 为图片/视频/音频;noteText 为文本)。 */
    @PostMapping("/{id}/media")
    public Result<Long> addMedia(@PathVariable Long id,
                                 @RequestParam String mediaType,
                                 @RequestParam(required = false) MultipartFile file,
                                 @RequestParam(required = false) String noteText) {
        byte[] bytes = null;
        String filename = null;
        if (file != null && !file.isEmpty()) {
            try { bytes = file.getBytes(); } catch (IOException e) {
                throw new com.sellm.common.BusinessException(com.sellm.common.ErrorCode.INVALID_INPUT, "文件读取失败");
            }
            filename = file.getOriginalFilename();
        }
        DiagnosisMedia m = appService.addMedia(id, mediaType, bytes, filename, noteText, List.of());
        return Result.ok(m.getId());
    }

    /** 生成结构化诊断(聚合已挂素材 + 结构化输入 + 量表知识库)。 */
    @PostMapping("/{id}/generate")
    public Result<DiagnosisResponse> generate(@PathVariable Long id,
                                              @RequestBody(required = false) GenerateDiagnosisRequest req) {
        List<String> names = req == null ? List.of() : (req.getSubjectNames() == null ? List.of() : req.getSubjectNames());
        return Result.ok(DiagnosisResponse.of(appService.generate(id, names)));
    }

    @PutMapping("/{id}")
    public Result<DiagnosisResponse> edit(@PathVariable Long id, @RequestBody EditDiagnosisBody body) {
        return Result.ok(DiagnosisResponse.of(appService.edit(id, body.getDraft())));
    }

    @PostMapping("/{id}/finalize")
    public Result<DiagnosisResponse> finalizeDiagnosis(@PathVariable Long id,
                                                       @RequestBody(required = false) EditDiagnosisBody body) {
        String content = body == null ? null : body.getContent();
        return Result.ok(DiagnosisResponse.of(appService.finalizeDiagnosis(id, content)));
    }

    @GetMapping("/{id}")
    public Result<DiagnosisResponse> get(@PathVariable Long id) {
        return Result.ok(DiagnosisResponse.of(appService.get(id)));
    }

    @GetMapping
    public Result<List<DiagnosisResponse>> listByChild(@RequestParam Long childId) {
        return Result.ok(appService.listByChild(childId).stream().map(DiagnosisResponse::of).toList());
    }

    @GetMapping("/{id}/pdf")
    public ResponseEntity<byte[]> downloadPdf(@PathVariable Long id) {
        Diagnosis d = appService.get(id);
        if (!"FINALIZED".equals(d.getStatus())) {
            throw new com.sellm.common.BusinessException(com.sellm.common.ErrorCode.INVALID_INPUT, "诊断未定稿,不可下载");
        }
        byte[] pdf = pdfExporter.toPdf("诊断报告", d.getFinalizedContent());
        return ResponseEntity.ok()
            .contentType(MediaType.APPLICATION_PDF)
            .header(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"diagnosis-" + id + ".pdf\"")
            .body(pdf);
    }

    /** 编辑/定稿请求体。 */
    public static class EditDiagnosisBody {
        private String draft;
        private String content;
        public String getDraft() { return draft; }
        public void setDraft(String draft) { this.draft = draft; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
