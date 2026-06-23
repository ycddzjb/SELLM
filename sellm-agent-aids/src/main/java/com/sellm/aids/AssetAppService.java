package com.sellm.aids;

import com.sellm.aids.dto.AssetResponse;
import com.sellm.aids.dto.GenerateAssetRequest;
import com.sellm.aids.dto.TaskStatusResponse;
import com.sellm.common.BusinessException;
import com.sellm.common.ErrorCode;
import com.sellm.storage.ObjectStorage;
import org.springframework.stereotype.Service;

import java.util.List;

/**
 * 文生素材编排:落 PENDING(独立提交)+ 触发 @Async 任务 + 查状态/产物。
 * 注意:submit 不加 @Transactional —— save(PENDING) 须先提交,@Async 后台线程才能 findById 查到(设计 §10)。
 */
@Service
public class AssetAppService {

    private final GeneratedAssetRepository repo;
    private final AssetGenerationTask task;
    private final ObjectStorage storage;

    public AssetAppService(GeneratedAssetRepository repo, AssetGenerationTask task, ObjectStorage storage) {
        this.repo = repo;
        this.task = task;
        this.storage = storage;
    }

    /** 受理生成任务:校验 → 落 PENDING → 触发异步 → 返回 taskId(= asset.id)。 */
    public Long submit(Long userId, GenerateAssetRequest req) {
        if (req.getPrompt() == null || req.getPrompt().isBlank())
            throw new BusinessException(ErrorCode.INVALID_INPUT, "生成提示不能为空");
        String type = normalizeType(req.getType());

        GeneratedAsset a = new GeneratedAsset();
        a.setOwnerId(userId);
        a.setType(type);
        a.setPrompt(req.getPrompt());
        a.setStatus(AssetStatus.PENDING.name());
        repo.save(a);
        a.setTaskId(String.valueOf(a.getId()));
        repo.update(a);

        // PENDING 已提交,触发后台任务(同步 executor 时同线程执行完)
        task.run(a.getId());
        return a.getId();
    }

    public TaskStatusResponse pollTask(Long userId, Long taskId) {
        GeneratedAsset a = requireOwned(userId, taskId);
        TaskStatusResponse.Result result = null;
        if (AssetStatus.SUCCESS.name().equals(a.getStatus())) {
            result = new TaskStatusResponse.Result(a.getType(), a.getStorageKey(), mimeOf(a.getStorageKey()));
        }
        return new TaskStatusResponse(a.getStatus(), result, a.getError());
    }

    public AssetResponse get(Long userId, Long id) {
        return toResponse(requireOwned(userId, id));
    }

    /** 取产物原始字节(行级权限);用于小程序预览/下载。返回 [bytes, mimeType]。 */
    public RawAsset getRaw(Long userId, Long id) {
        GeneratedAsset a = requireOwned(userId, id);
        if (!AssetStatus.SUCCESS.name().equals(a.getStatus()) || a.getStorageKey() == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "素材尚未生成完成");
        byte[] bytes = storage.get(a.getStorageKey());
        if (bytes == null)
            throw new BusinessException(ErrorCode.NOT_FOUND, "产物不存在");
        return new RawAsset(bytes, mimeOf(a.getStorageKey()));
    }

    /** 原始产物载体。 */
    public record RawAsset(byte[] bytes, String mimeType) {}

    public List<AssetResponse> listMine(Long userId) {
        return repo.listByOwner(userId).stream()
            .map(this::toResponse)
            .toList();
    }

    private String normalizeType(String raw) {
        if (raw == null || raw.isBlank()) return AssetType.IMAGE.name();
        try {
            return AssetType.valueOf(raw.trim().toUpperCase()).name();
        } catch (IllegalArgumentException e) {
            throw new BusinessException(ErrorCode.INVALID_INPUT, "不支持的素材类型: " + raw);
        }
    }

    private GeneratedAsset requireOwned(Long userId, Long id) {
        GeneratedAsset a = repo.findById(id);
        if (a == null) throw new BusinessException(ErrorCode.NOT_FOUND, "素材任务不存在");
        if (!a.getOwnerId().equals(userId)) throw new BusinessException(ErrorCode.ACCESS_DENIED, "无权访问该素材");
        return a;
    }

    private AssetResponse toResponse(GeneratedAsset a) {
        return new AssetResponse(a.getId(), a.getType(), a.getStatus(),
            a.getStorageKey(), mimeOf(a.getStorageKey()), a.getError());
    }

    /** 据产物 key 后缀推断 mimeType,便于小程序判断是否可图片预览。 */
    private String mimeOf(String key) {
        if (key == null) return null;
        String lower = key.toLowerCase();
        if (lower.endsWith(".png")) return "image/png";
        if (lower.endsWith(".jpg") || lower.endsWith(".jpeg")) return "image/jpeg";
        if (lower.endsWith(".wav")) return "audio/wav";
        if (lower.endsWith(".mp3")) return "audio/mpeg";
        if (lower.endsWith(".mp4")) return "video/mp4";
        if (lower.endsWith(".txt")) return "text/plain";
        return "application/octet-stream";
    }
}
