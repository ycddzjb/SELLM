package com.sellm.scale;

import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.common.Result;
import com.sellm.scale.dto.ScaleItemDto;
import com.sellm.scale.dto.ScaleRequest;
import com.sellm.scale.dto.ScaleResponse;
import com.sellm.scale.dto.ScoreBandDto;
import org.springframework.web.bind.annotation.*;

import java.util.ArrayList;
import java.util.List;

/**
 * 量表库管理:超管维护(写),已登录用户可读(评估时老师动态取量表定义)。
 * 端点级授权在 SecurityConfig:写仅 SUPER_ADMIN,读 authenticated。
 */
@RestController
@RequestMapping("/api/scales")
public class ScaleController {

    private final ScaleRepository repository;

    public ScaleController(ScaleRepository repository) {
        this.repository = repository;
    }

    /** 列表(可选 ?disorderType= 过滤),仅头信息。 */
    @GetMapping
    public Result<List<ScaleResponse>> list(@RequestParam(required = false) String disorderType) {
        List<Scale> scales = (disorderType == null || disorderType.isBlank())
            ? repository.listAll()
            : repository.listByDisorderType(disorderType);
        List<ScaleResponse> out = new ArrayList<>();
        for (Scale s : scales) {
            out.add(ScaleResponse.of(s, false));
        }
        return Result.ok(out);
    }

    /** 详情(含题目 + 分段)。 */
    @GetMapping("/{scaleId}")
    public Result<ScaleResponse> detail(@PathVariable String scaleId) {
        Scale scale = repository.findById(scaleId);
        if (scale == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "量表不存在");
        }
        return Result.ok(ScaleResponse.of(scale, true));
    }

    /** 创建(超管)。 */
    @PostMapping
    public Result<String> create(@RequestBody ScaleRequest req) {
        if (req.getScaleId() == null || req.getScaleId().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "请填写量表ID");
        }
        if (repository.findById(req.getScaleId()) != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "量表ID已存在");
        }
        repository.save(toScale(req.getScaleId(), req));
        return Result.ok(req.getScaleId());
    }

    /** 更新(超管):以路径 scaleId 为准。 */
    @PutMapping("/{scaleId}")
    public Result<Void> update(@PathVariable String scaleId, @RequestBody ScaleRequest req) {
        if (repository.findById(scaleId) == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "量表不存在");
        }
        repository.update(toScale(scaleId, req));
        return Result.ok(null);
    }

    /** 删除(超管)。 */
    @DeleteMapping("/{scaleId}")
    public Result<Void> delete(@PathVariable String scaleId) {
        if (repository.findById(scaleId) == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "量表不存在");
        }
        repository.deleteById(scaleId);
        return Result.ok(null);
    }

    private Scale toScale(String scaleId, ScaleRequest req) {
        List<ScaleItem> items = new ArrayList<>();
        if (req.getItems() != null) {
            for (ScaleItemDto d : req.getItems()) {
                items.add(new ScaleItem(d.getItemId(), d.getStem(), d.getDimension(),
                    d.getSortOrder(), d.getMaxScore()));
            }
        }
        ScoringRule rule = null;
        if (req.getBands() != null && !req.getBands().isEmpty()) {
            List<ScoreBand> bands = new ArrayList<>();
            for (ScoreBandDto d : req.getBands()) {
                bands.add(new ScoreBand(d.getLowerBound(), d.getUpperBound(),
                    d.getLabel(), d.getInterpretation()));
            }
            rule = new ScoringRule(bands);
        }
        return new Scale(scaleId, req.getName(), req.getVersion(),
            req.getDisorderType(), req.getDescription(), items, rule);
    }
}
