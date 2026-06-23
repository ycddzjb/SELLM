package com.sellm.aids;

import com.sellm.agentcommon.SmartLayerException;
import com.sellm.anonymizer.AnonymizationException;
import com.sellm.anonymizer.AnonymizationResult;
import com.sellm.anonymizer.Anonymizer;
import com.sellm.storage.ObjectStorage;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.List;

/**
 * 文生素材后台任务:脱敏 → Python → ObjectStorage → 状态机。
 * 红线:脱敏失败硬阻断(绝不出网原文)→ status=FAILED;全程 catch 兜底,后台线程不崩。
 * 可见性:submit 已独立提交 PENDING 行,任务内 findById 重查可见(详见设计 §10)。
 */
@Component
public class AssetGenerationTask {

    private static final Logger log = LoggerFactory.getLogger(AssetGenerationTask.class);

    private final GeneratedAssetRepository repo;
    private final SmartLayerClient smartLayer;
    private final Anonymizer anonymizer;
    private final ObjectStorage storage;

    public AssetGenerationTask(GeneratedAssetRepository repo, SmartLayerClient smartLayer,
                               Anonymizer anonymizer, ObjectStorage storage) {
        this.repo = repo;
        this.smartLayer = smartLayer;
        this.anonymizer = anonymizer;
        this.storage = storage;
    }

    @Async("assetTaskExecutor")
    public void run(Long assetId) {
        GeneratedAsset a = repo.findById(assetId);
        if (a == null) {
            log.warn("文生素材任务找不到 asset id={}", assetId);
            return;
        }
        try {
            a.setStatus(AssetStatus.RUNNING.name());
            repo.update(a);

            String content;
            GeneratedContent generated;
            try {
                AnonymizationResult anon = anonymizer.anonymize(a.getPrompt(), List.of(), List.of());
                generated = smartLayer.generate(a.getType(), anon.getAnonymizedText());
                content = anonymizer.restore(generated.getText(), anon.getRestoreMap());
            } catch (AnonymizationException ae) {
                fail(a, "脱敏校验未通过,已阻断出网");
                return;
            } catch (SmartLayerException se) {
                fail(a, "智能层调用失败");
                return;
            }

            // 有媒体二进制 → 按 ext/mimeType 落盘;否则存文本描述为 .txt(VIDEO 等降级)
            String key;
            if (generated.hasMedia()) {
                key = "asset/" + a.getId() + "." + generated.getExt();
                storage.put(key, generated.getMedia(), generated.getMimeType());
            } else {
                key = "asset/" + a.getId() + ".txt";
                storage.put(key, content.getBytes(StandardCharsets.UTF_8), "text/plain");
            }
            a.setStorageKey(key);
            a.setStatus(AssetStatus.SUCCESS.name());
            a.setError(null);
            repo.update(a);
        } catch (Exception e) {
            log.error("文生素材任务异常 id={}", assetId, e);
            fail(a, "素材生成失败:" + e.getMessage());
        }
    }

    private void fail(GeneratedAsset a, String error) {
        a.setStatus(AssetStatus.FAILED.name());
        a.setError(error);
        repo.update(a);
    }
}
