package com.sellm.training;

import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.common.Result;
import com.sellm.training.dto.CreateCycleRequest;
import com.sellm.training.dto.CycleResponse;
import com.sellm.training.dto.RecordResponse;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 训练周期 + 训练数据。端点级 RBAC 由 SecurityConfig(写限 TEACHER/MANAGER);行级 AccessGuard。
 * 周期串起诊断→IEP→训练数据→阶段评估;阶段评估/新IEP 端点见后续步骤。
 */
@RestController
@RequestMapping("/api/training-cycles")
public class TrainingCycleController {

    private final TrainingCycleAppService appService;

    public TrainingCycleController(TrainingCycleAppService appService) {
        this.appService = appService;
    }

    @PostMapping
    public Result<CycleResponse> create(@RequestBody CreateCycleRequest req) {
        return Result.ok(CycleResponse.of(
            appService.createCycle(req.getChildId(), req.getDiagnosisId(), req.getIepId(), req.getTitle())));
    }

    @GetMapping("/{id}")
    public Result<CycleResponse> get(@PathVariable Long id) {
        return Result.ok(CycleResponse.of(appService.getCycle(id)));
    }

    @GetMapping
    public Result<List<CycleResponse>> listByChild(@RequestParam Long childId) {
        return Result.ok(appService.listByChild(childId).stream().map(CycleResponse::of).toList());
    }

    @PostMapping("/{id}/close")
    public Result<CycleResponse> close(@PathVariable Long id) {
        return Result.ok(CycleResponse.of(appService.closeCycle(id)));
    }

    /** 挂多模态训练数据 + 指标得分(scores JSON)。 */
    @PostMapping("/{id}/records")
    public Result<RecordResponse> addRecord(@PathVariable Long id,
                                            @RequestParam String mediaType,
                                            @RequestParam(required = false) MultipartFile file,
                                            @RequestParam(required = false) String noteText,
                                            @RequestParam(required = false) String scores) {
        byte[] bytes = null;
        String filename = null;
        if (file != null && !file.isEmpty()) {
            try { bytes = file.getBytes(); } catch (IOException e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "文件读取失败");
            }
            filename = file.getOriginalFilename();
        }
        TrainingRecord r = appService.addRecord(id, mediaType, bytes, filename, noteText, scores, List.of());
        return Result.ok(RecordResponse.of(r));
    }

    @GetMapping("/{id}/records")
    public Result<List<RecordResponse>> listRecords(@PathVariable Long id) {
        return Result.ok(appService.listRecords(id).stream().map(RecordResponse::of).toList());
    }

    // ── 阶段评估 ──

    /** 生成阶段评估(汇总本周期得分 → 对比上一周期 → 量化 delta + AI 叙述)。 */
    @PostMapping("/{id}/stage-eval")
    public Result<com.sellm.training.dto.StageEvalResponse> generateStageEval(@PathVariable Long id) {
        return Result.ok(com.sellm.training.dto.StageEvalResponse.of(
            appService.generateStageEval(id, List.of())));
    }

    @PutMapping("/stage-evals/{evalId}")
    public Result<com.sellm.training.dto.StageEvalResponse> editStageEval(
            @PathVariable Long evalId, @RequestBody EvalBody body) {
        return Result.ok(com.sellm.training.dto.StageEvalResponse.of(
            appService.editStageEval(evalId, body.getDraft())));
    }

    @PostMapping("/stage-evals/{evalId}/finalize")
    public Result<com.sellm.training.dto.StageEvalResponse> finalizeStageEval(
            @PathVariable Long evalId, @RequestBody(required = false) EvalBody body) {
        return Result.ok(com.sellm.training.dto.StageEvalResponse.of(
            appService.finalizeStageEval(evalId, body == null ? null : body.getContent())));
    }

    @GetMapping("/stage-evals/{evalId}")
    public Result<com.sellm.training.dto.StageEvalResponse> getStageEval(@PathVariable Long evalId) {
        return Result.ok(com.sellm.training.dto.StageEvalResponse.of(appService.getStageEval(evalId)));
    }

    /** 纵向对比:某 child 各周期阶段评估(供前端画趋势)。 */
    @GetMapping("/compare")
    public Result<List<com.sellm.training.dto.StageEvalResponse>> compare(@RequestParam Long childId) {
        return Result.ok(appService.compareByChild(childId).stream()
            .map(com.sellm.training.dto.StageEvalResponse::of).toList());
    }

    /** 据本周期阶段评估的适配性建议,生成优化后的新版 IEP 草案(关联本周期)。 */
    @PostMapping("/{id}/next-iep")
    public Result<Map<String, Object>> nextIep(@PathVariable Long id) {
        com.sellm.iep.IepRecord rec = appService.generateNextIep(id);
        Map<String, Object> out = new java.util.HashMap<>();
        out.put("id", rec.getId());
        out.put("childId", rec.getChildId());
        out.put("cycleId", rec.getCycleId());
        out.put("draft", rec.getDraft());
        out.put("status", rec.getStatus());
        return Result.ok(out);
    }

    public static class EvalBody {
        private String draft;
        private String content;
        public String getDraft() { return draft; }
        public void setDraft(String draft) { this.draft = draft; }
        public String getContent() { return content; }
        public void setContent(String content) { this.content = content; }
    }
}
