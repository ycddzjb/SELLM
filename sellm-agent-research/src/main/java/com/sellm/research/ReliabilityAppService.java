package com.sellm.research;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.research.dto.ReliabilityRequest;
import com.sellm.research.dto.ReliabilityResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ReliabilityAppService {

    private final ReliabilityService reliabilityService;
    private final ReliabilityCalcRepository repo;
    private final ObjectMapper json = new ObjectMapper();

    public ReliabilityAppService(ReliabilityService reliabilityService, ReliabilityCalcRepository repo) {
        this.reliabilityService = reliabilityService;
        this.repo = repo;
    }

    public ReliabilityResponse compute(Long userId, ReliabilityRequest req) {
        ReliabilityResult result;
        try {
            result = reliabilityService.compute(req.getScores());
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, e.getMessage());
        }
        ReliabilityCalc c = new ReliabilityCalc();
        c.setOwnerId(userId);
        c.setMethod(req.getMethod() == null ? "cronbach+splithalf+itemtotal" : req.getMethod());
        try {
            c.setDataset(json.writeValueAsString(req.getScores()));
            c.setResult(json.writeValueAsString(result));
        } catch (Exception e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "序列化失败");
        }
        repo.save(c);
        return new ReliabilityResponse(c.getId(), result);
    }

    public ReliabilityResponse get(Long userId, Long id) {
        ReliabilityCalc c = repo.findById(id);
        if (c == null) throw new BusinessException(ErrorCode.NOT_FOUND, "记录不存在");
        if (!c.getOwnerId().equals(userId)) throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问");
        ReliabilityResult result;
        try { result = json.readValue(c.getResult(), ReliabilityResult.class); }
        catch (Exception e) { throw new BusinessException(ErrorCode.INVALID_INPUT, "结果反序列化失败"); }
        return new ReliabilityResponse(c.getId(), result);
    }

    public List<ReliabilityCalc> listMine(Long userId) {
        return repo.listByOwner(userId);
    }
}
