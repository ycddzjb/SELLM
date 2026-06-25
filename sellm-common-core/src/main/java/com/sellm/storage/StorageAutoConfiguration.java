package com.sellm.storage;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.AutoConfiguration;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.context.annotation.Bean;

/**
 * 自动装配默认对象存储(NoopObjectStorage,本地落盘)。
 * 任何依赖 sellm-common-core 的模块无需组件扫描即可获得 ObjectStorage。
 * 若上层已定义 ObjectStorage bean(如 backend 的 StorageConfig 可切 MinIO),则本默认退让。
 */
@AutoConfiguration
public class StorageAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(ObjectStorage.class)
    public ObjectStorage noopObjectStorage(
            @Value("${sellm.storage.local-dir:data/media}") String localDir) {
        return new NoopObjectStorage(localDir);
    }
}
