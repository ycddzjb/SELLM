package com.sellm.storage;

import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 按 sellm.minio.provider 装配对象存储。
 * provider=minio 且 endpoint 非空 → MinIO;否则 Noop(默认,本地落盘,不外联)。
 */
@Configuration
@EnableConfigurationProperties(StorageProperties.class)
public class StorageConfig {

    @Bean
    public ObjectStorage objectStorage(StorageProperties props) {
        if ("minio".equalsIgnoreCase(props.getProvider())
                && props.getEndpoint() != null && !props.getEndpoint().isBlank()) {
            return new MinioObjectStorage(props);
        }
        return new NoopObjectStorage(props.getLocalDir());
    }
}
