package com.sellm.assessment.media;

import com.sellm.assessment.media.dto.MediaResponse;
import com.sellm.assessment.media.dto.SuggestionResponse;
import com.sellm.child.Child;
import com.sellm.child.ChildRepository;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.common.Result;
import com.sellm.multimodal.ItemSuggestion;
import com.sellm.multimodal.MultimodalModel;
import com.sellm.scale.Scale;
import com.sellm.scale.ScaleItem;
import com.sellm.scale.ScaleRepository;
import com.sellm.security.AccessGuard;
import com.sellm.security.AuthPrincipal;
import com.sellm.security.CurrentUser;
import com.sellm.storage.ObjectStorage;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import java.util.ArrayList;
import java.util.List;

/**
 * 评估媒体:多模态素材上传 + 识别建议。行级权限复用 AccessGuard。
 * AI 仅产指标评分建议,老师确认后走现有 /api/assessments 提交正式评估。
 */
@RestController
@RequestMapping("/api/children/{childId}/evaluation-media")
public class EvaluationMediaController {

    private final EvaluationMediaRepository mediaRepository;
    private final ChildRepository childRepository;
    private final ScaleRepository scaleRepository;
    private final ObjectStorage objectStorage;
    private final MultimodalModel multimodalModel;
    private final CurrentUser currentUser;
    private final AccessGuard accessGuard;

    public EvaluationMediaController(EvaluationMediaRepository mediaRepository, ChildRepository childRepository,
                                     ScaleRepository scaleRepository, ObjectStorage objectStorage,
                                     MultimodalModel multimodalModel, CurrentUser currentUser, AccessGuard accessGuard) {
        this.mediaRepository = mediaRepository;
        this.childRepository = childRepository;
        this.scaleRepository = scaleRepository;
        this.objectStorage = objectStorage;
        this.multimodalModel = multimodalModel;
        this.currentUser = currentUser;
        this.accessGuard = accessGuard;
    }

    /** 上传素材(图片/视频用 multipart file;纯笔记用 noteText)。 */
    @PostMapping
    public Result<Long> upload(@PathVariable Long childId,
                               @RequestParam(required = false) MultipartFile file,
                               @RequestParam(required = false) String noteText,
                               @RequestParam(required = false) String scaleId,
                               @RequestParam String mediaType) {
        AuthPrincipal me = requireAccess(childId);
        String objectKey = null;
        if (file != null && !file.isEmpty()) {
            try {
                objectKey = "child/" + childId + "/" + System.nanoTime() + "_" + sanitize(file.getOriginalFilename());
                objectStorage.put(objectKey, file.getBytes(), file.getContentType());
            } catch (java.io.IOException e) {
                throw new BusinessException(ErrorCode.INVALID_INPUT, "文件读取失败");
            }
        }
        if (objectKey == null && (noteText == null || noteText.isBlank())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "请上传文件或填写训练笔记");
        }
        EvaluationMedia saved = mediaRepository.save(new EvaluationMedia(
            null, childId, scaleId, mediaType, objectKey, noteText, me.getUserId(), "UPLOADED"));
        return Result.ok(saved.getId());
    }

    /** 触发识别:多模态模型据素材 + 目标量表 items 给评分建议(仅建议,不落正式评估)。 */
    @PostMapping("/{mediaId}/analyze")
    public Result<List<SuggestionResponse>> analyze(@PathVariable Long childId, @PathVariable Long mediaId) {
        requireAccess(childId);
        EvaluationMedia media = mediaRepository.findById(mediaId);
        if (media == null || !childId.equals(media.getChildId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "媒体不存在");
        }
        if (media.getScaleId() == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "该素材未指定量表,无法识别");
        }
        Scale scale = scaleRepository.findById(media.getScaleId());
        if (scale == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "量表不存在");
        }
        byte[] bytes = media.getObjectKey() == null ? null : objectStorage.get(media.getObjectKey());
        List<ItemSuggestion> suggestions = multimodalModel.analyze(bytes, media.getNoteText(), scale.getItems());
        mediaRepository.updateStatus(mediaId, "ANALYZED");

        List<SuggestionResponse> out = new ArrayList<>();
        for (ItemSuggestion s : suggestions) {
            out.add(new SuggestionResponse(s.getItemId(), s.getSuggestedScore(), s.getReason()));
        }
        return Result.ok(out);
    }

    @GetMapping
    public Result<List<MediaResponse>> list(@PathVariable Long childId,
                                            @RequestParam(required = false) String type) {
        requireAccess(childId);
        List<EvaluationMedia> medias = (type == null || type.isBlank())
            ? mediaRepository.listByChild(childId)
            : mediaRepository.listByChildAndType(childId, type);
        List<MediaResponse> out = new ArrayList<>();
        for (EvaluationMedia m : medias) {
            out.add(MediaResponse.of(m));
        }
        return Result.ok(out);
    }

    /** 儿童存在 + 行级权限(越权 403)。 */
    private AuthPrincipal requireAccess(Long childId) {
        AuthPrincipal me = currentUser.require();
        Child child = childRepository.findById(childId);
        if (child == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "儿童档案不存在");
        }
        accessGuard.checkChildAccess(me, child);
        return me;
    }

    private String sanitize(String name) {
        if (name == null || name.isBlank()) {
            return "file";
        }
        return name.replaceAll("[^a-zA-Z0-9._-]", "_");
    }
}
