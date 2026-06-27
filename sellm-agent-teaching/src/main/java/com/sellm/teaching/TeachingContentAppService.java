package com.sellm.teaching;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.sellm.agentcommon.SmartLayerException;
import com.sellm.anonymizer.AnonymizationException;
import com.sellm.anonymizer.AnonymizationResult;
import com.sellm.anonymizer.Anonymizer;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.teaching.dto.GenerateContentRequest;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 * 教学模块内容生成(教案/课件/案例/习题,统一模型)。
 * 出网前脱敏(subjectNames 屏蔽表)→ Python → 还原;AI 只产 DRAFT,人工定稿冻结。
 * 不加类级 @Transactional:含出网 HTTP,DRAFT 先落库再出网(对齐项目其他 agent)。
 */
@Service
public class TeachingContentAppService {

    private static final Set<String> TYPES = Set.of("PLAN", "LESSON", "COURSEWARE", "EXERCISE");

    private final TeachingContentRepository repo;
    private final SmartLayerClient smartLayer;
    private final Anonymizer anonymizer;
    private final ObjectMapper json = new ObjectMapper();

    public TeachingContentAppService(TeachingContentRepository repo, SmartLayerClient smartLayer, Anonymizer anonymizer) {
        this.repo = repo;
        this.smartLayer = smartLayer;
        this.anonymizer = anonymizer;
    }

    public TeachingContent generate(Long userId, GenerateContentRequest req) {
        String type = req.getContentType() == null ? "" : req.getContentType().toUpperCase();
        if (!TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "不支持的内容类型: " + req.getContentType());
        }
        if (req.getRequirement() == null || req.getRequirement().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "请输入内容与要求");
        }
        String optionsJson = writeJson(req.getOptions());

        // 1. 先落 DRAFT
        TeachingContent c = new TeachingContent();
        c.setOwnerId(userId);
        c.setContentType(type);
        c.setTitle(req.getTitle());
        c.setOptions(optionsJson);
        c.setStatus("DRAFT");
        repo.save(c);

        // 2. 脱敏 → Python → 还原
        String requirement = (req.getTitle() == null ? "" : req.getTitle() + "\n") + req.getRequirement();
        String content;
        try {
            AnonymizationResult anon = anonymizer.anonymize(requirement, safeNames(req.getSubjectNames()), List.of());
            String ai = smartLayer.generateContent(type, anon.getAnonymizedText(), optionsJson);
            content = anonymizer.restore(ai, anon.getRestoreMap());
        } catch (AnonymizationException ae) {
            throw new BusinessException(ErrorCode.ANONYMIZATION_FAILED, "脱敏校验未通过,已阻断出网");
        } catch (SmartLayerException se) {
            content = "AI 生成失败,可重试或手动撰写。";
        }
        c.setAiDraft(content);
        c.setContent(content);
        repo.update(c);
        return c;
    }

    // ── 无状态生成(草稿不落库):教案两步 + 课件;只脱敏→出网→还原,不碰 repo ──

    /** 生成"提示词"草稿(可编辑),不落库。 */
    public String genPromptDraft(GenerateContentRequest req) {
        String type = normType(req.getContentType());
        requireRequirement(req.getRequirement());
        String optionsJson = writeJson(req.getOptions());
        String text = (req.getTitle() == null ? "" : req.getTitle() + "\n") + req.getRequirement();
        return outbound(text, req.getSubjectNames(),
            (safe) -> smartLayer.generatePrompt(type, safe, optionsJson));
    }

    /** 据提示词生成正文草稿(可编辑),不落库。 */
    public String genContentDraft(GenerateContentRequest req) {
        String type = normType(req.getContentType());
        if (req.getRequirement() == null || req.getRequirement().isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "请输入提示词");
        }
        String optionsJson = writeJson(req.getOptions());
        return outbound(req.getRequirement(), req.getSubjectNames(),   // requirement 此处承载提示词
            (safe) -> smartLayer.generateContent(type, safe, optionsJson));
    }

    /** 基于定稿教案正文生成课件草稿(可编辑),不落库。 */
    public String genCoursewareDraft(Long userId, Long lessonId, List<String> subjectNames) {
        TeachingContent lesson = requireOwned(userId, lessonId);
        if (!"LESSON".equals(lesson.getContentType()) || !"FINALIZED".equals(lesson.getStatus())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "请选择已定稿的教案");
        }
        String optionsJson = lesson.getOptions();
        return outbound(lesson.getContent(), subjectNames,
            (safe) -> smartLayer.generateCoursewareFromLesson(safe, optionsJson));
    }

    /** 定稿保存:一次性落库 FINALIZED(草稿态此前不入库)。 */
    public TeachingContent finalizeNew(Long userId, GenerateContentRequest req, String content, Long sourceId) {
        String type = normType(req.getContentType());
        if (content == null || content.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "定稿内容不能为空");
        }
        TeachingContent c = new TeachingContent();
        c.setOwnerId(userId);
        c.setContentType(type);
        c.setTitle(req.getTitle());
        c.setOptions(writeJson(req.getOptions()));
        c.setAiDraft(content);
        c.setContent(content);
        c.setStatus("FINALIZED");
        c.setSourceId(sourceId);
        return repo.save(c);
    }

    /** 脱敏→出网→还原 通用封装;脱敏失败硬阻断。 */
    private String outbound(String text, List<String> names, java.util.function.Function<String, String> call) {
        try {
            AnonymizationResult anon = anonymizer.anonymize(text, safeNames(names), List.of());
            String ai = call.apply(anon.getAnonymizedText());
            return anonymizer.restore(ai, anon.getRestoreMap());
        } catch (AnonymizationException ae) {
            throw new BusinessException(ErrorCode.ANONYMIZATION_FAILED, "脱敏校验未通过,已阻断出网");
        } catch (SmartLayerException se) {
            return "AI 生成失败,可重试或手动撰写。";
        }
    }

    private String normType(String t) {
        String type = t == null ? "" : t.toUpperCase();
        if (!TYPES.contains(type)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "不支持的内容类型: " + t);
        }
        return type;
    }

    private void requireRequirement(String r) {
        if (r == null || r.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "请输入内容与要求");
        }
    }
    public TeachingContent edit(Long userId, Long id, String content) {
        TeachingContent c = requireOwned(userId, id);
        if ("FINALIZED".equals(c.getStatus()))
            throw new BusinessException(ErrorCode.INVALID_INPUT, "已定稿不可编辑");
        if (content == null || content.isBlank())
            throw new BusinessException(ErrorCode.INVALID_INPUT, "内容不能为空");
        c.setContent(content);
        repo.update(c);
        return c;
    }

    public TeachingContent finalizeContent(Long userId, Long id) {
        TeachingContent c = requireOwned(userId, id);
        c.setStatus("FINALIZED");
        repo.update(c);
        return c;
    }

    public TeachingContent get(Long userId, Long id) {
        return requireOwned(userId, id);
    }

    public void delete(Long userId, Long id) {
        requireOwned(userId, id);   // 行级校验:仅本人可删
        repo.softDelete(id);
    }

    public List<TeachingContent> list(Long userId, String contentType) {
        return repo.listByOwnerAndType(userId, contentType == null ? "" : contentType.toUpperCase());
    }

    private TeachingContent requireOwned(Long userId, Long id) {
        TeachingContent c = repo.findById(id);
        if (c == null) throw new BusinessException(ErrorCode.NOT_FOUND, "内容不存在");
        if (!c.getOwnerId().equals(userId)) throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问");
        return c;
    }

    private static List<String> safeNames(List<String> names) {
        if (names == null) return List.of();
        return names.stream().filter(n -> n != null && !n.isBlank()).toList();
    }

    private String writeJson(Object o) {
        if (o == null) return null;
        try { return json.writeValueAsString(o); } catch (Exception e) { return null; }
    }
}
