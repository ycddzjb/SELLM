package com.sellm.research;

import com.sellm.agentcommon.SmartLayerException;
import com.sellm.anonymizer.AnonymizationException;
import com.sellm.anonymizer.AnonymizationResult;
import com.sellm.anonymizer.Anonymizer;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.research.dto.EditRequest;
import com.sellm.research.dto.GenerateProposalRequest;
import com.sellm.research.dto.ProposalResponse;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
public class ProposalAppService {

    private final ResearchProposalRepository repo;
    private final SmartLayerClient smartLayer;
    private final Anonymizer anonymizer;

    public ProposalAppService(ResearchProposalRepository repo, SmartLayerClient smartLayer, Anonymizer anonymizer) {
        this.repo = repo;
        this.smartLayer = smartLayer;
        this.anonymizer = anonymizer;
    }

    public ProposalResponse generate(Long userId, GenerateProposalRequest req) {
        if (req.getTopic() == null || req.getTopic().isBlank())
            throw new BusinessException(ErrorCode.INVALID_INPUT, "课题主题不能为空");
        ResearchProposal p = new ResearchProposal();
        p.setOwnerId(userId);
        p.setTopic(req.getTopic());
        p.setStatus("DRAFT");
        repo.save(p);
        String content;
        try {
            AnonymizationResult anon = anonymizer.anonymize(req.getTopic(),
                safeNames(req.getSubjectNames()), List.of());
            String aiText = smartLayer.generate(anon.getAnonymizedText());
            content = anonymizer.restore(aiText, anon.getRestoreMap());
        } catch (AnonymizationException ae) {
            throw new BusinessException(ErrorCode.ANONYMIZATION_FAILED, "脱敏校验未通过,已阻断出网");
        } catch (SmartLayerException se) {
            content = "AI 生成失败,可重试或手动撰写。";
        }
        p.setAiDraft(content);
        p.setContent(content);
        repo.update(p);
        return resp(p);
    }

    public ProposalResponse edit(Long userId, Long id, EditRequest req) {
        ResearchProposal p = requireOwned(userId, id);
        if ("FINALIZED".equals(p.getStatus()))
            throw new BusinessException(ErrorCode.INVALID_INPUT, "已定稿不可编辑");
        if (req.getContent() == null || req.getContent().isBlank())
            throw new BusinessException(ErrorCode.INVALID_INPUT, "内容不能为空");
        p.setContent(req.getContent());
        repo.update(p);
        return resp(p);
    }

    public ProposalResponse finalizeProposal(Long userId, Long id) {
        ResearchProposal p = requireOwned(userId, id);
        p.setStatus("FINALIZED");
        repo.update(p);
        return resp(p);
    }

    public ProposalResponse get(Long userId, Long id) {
        return resp(requireOwned(userId, id));
    }

    public List<ResearchProposal> listMine(Long userId) {
        return repo.listByOwner(userId);
    }

    /** subjectNames 安全转 List:null→空,过滤空白(供脱敏屏蔽表)。 */
    private static List<String> safeNames(List<String> names) {
        if (names == null) return List.of();
        return names.stream().filter(n -> n != null && !n.isBlank()).toList();
    }

    private ResearchProposal requireOwned(Long userId, Long id) {
        ResearchProposal p = repo.findById(id);
        if (p == null) throw new BusinessException(ErrorCode.NOT_FOUND, "课题书不存在");
        if (!p.getOwnerId().equals(userId)) throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问");
        return p;
    }

    private ProposalResponse resp(ResearchProposal p) {
        return new ProposalResponse(p.getId(), p.getStatus(), p.getContent(), p.getAiDraft());
    }
}
