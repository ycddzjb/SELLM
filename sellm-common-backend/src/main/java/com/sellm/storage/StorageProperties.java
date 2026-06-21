package com.sellm.storage;

import org.springframework.boot.context.properties.ConfigurationProperties;

/** 对象存储配置(sellm.minio.*)。默认 provider=noop(本地临时目录,不连 MinIO)。 */
@ConfigurationProperties(prefix = "sellm.minio")
public class StorageProperties {
    private String provider = "noop";   // noop | minio
    private String endpoint = "";
    private String accessKey = "";
    private String secretKey = "";
    private String bucket = "sellm-media";
    /** noop 模式下的本地落盘目录 */
    private String localDir = "data/media";

    public String getProvider() { return provider; }
    public void setProvider(String provider) { this.provider = provider; }
    public String getEndpoint() { return endpoint; }
    public void setEndpoint(String endpoint) { this.endpoint = endpoint; }
    public String getAccessKey() { return accessKey; }
    public void setAccessKey(String accessKey) { this.accessKey = accessKey; }
    public String getSecretKey() { return secretKey; }
    public void setSecretKey(String secretKey) { this.secretKey = secretKey; }
    public String getBucket() { return bucket; }
    public void setBucket(String bucket) { this.bucket = bucket; }
    public String getLocalDir() { return localDir; }
    public void setLocalDir(String localDir) { this.localDir = localDir; }
}
