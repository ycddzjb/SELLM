package com.sellm.storage;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;

/**
 * 默认对象存储:落本地目录,不连任何外部服务。
 * 用于 dev/test 与未配置 MinIO 的环境,保证零外部依赖。
 */
public class NoopObjectStorage implements ObjectStorage {

    private final Path baseDir;

    public NoopObjectStorage(String localDir) {
        this.baseDir = Paths.get(localDir);
    }

    @Override
    public String put(String key, byte[] data, String contentType) {
        try {
            Path target = resolve(key);
            Files.createDirectories(target.getParent());
            Files.write(target, data);
            return key;
        } catch (IOException e) {
            throw new RuntimeException("本地存储写入失败: " + key, e);
        }
    }

    @Override
    public byte[] get(String key) {
        try {
            Path target = resolve(key);
            if (!Files.isRegularFile(target)) {
                return null;
            }
            return Files.readAllBytes(target);
        } catch (IOException e) {
            throw new RuntimeException("本地存储读取失败: " + key, e);
        }
    }

    @Override
    public String reference(String key) {
        return "local://" + key;
    }

    /** 防目录穿越:key 规范化后仍须在 baseDir 下。 */
    private Path resolve(String key) {
        Path target = baseDir.resolve(key).normalize();
        if (!target.startsWith(baseDir.normalize())) {
            throw new IllegalArgumentException("非法对象 key: " + key);
        }
        return target;
    }
}
