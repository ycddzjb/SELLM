package com.sellm.storage;

import io.minio.GetObjectArgs;
import io.minio.GetPresignedObjectUrlArgs;
import io.minio.MinioClient;
import io.minio.PutObjectArgs;
import io.minio.http.Method;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.util.concurrent.TimeUnit;

/**
 * MinIO 对象存储(provider=minio 时启用)。
 * 懒连接:MinioClient 构造不发起网络,首个 put/get 才连。凭据走环境变量,不入库。
 */
public class MinioObjectStorage implements ObjectStorage {

    private final MinioClient client;
    private final String bucket;

    public MinioObjectStorage(StorageProperties props) {
        this.bucket = props.getBucket();
        this.client = MinioClient.builder()
            .endpoint(props.getEndpoint())
            .credentials(props.getAccessKey(), props.getSecretKey())
            .build();
    }

    @Override
    public String put(String key, byte[] data, String contentType) {
        try (InputStream in = new ByteArrayInputStream(data)) {
            client.putObject(PutObjectArgs.builder()
                .bucket(bucket).object(key)
                .stream(in, data.length, -1)
                .contentType(contentType == null ? "application/octet-stream" : contentType)
                .build());
            return key;
        } catch (Exception e) {
            throw new RuntimeException("MinIO 写入失败: " + key, e);
        }
    }

    @Override
    public byte[] get(String key) {
        try (InputStream in = client.getObject(GetObjectArgs.builder()
                .bucket(bucket).object(key).build())) {
            return in.readAllBytes();
        } catch (Exception e) {
            throw new RuntimeException("MinIO 读取失败: " + key, e);
        }
    }

    @Override
    public String reference(String key) {
        try {
            return client.getPresignedObjectUrl(GetPresignedObjectUrlArgs.builder()
                .method(Method.GET).bucket(bucket).object(key)
                .expiry(1, TimeUnit.HOURS).build());
        } catch (Exception e) {
            throw new RuntimeException("MinIO 预签名失败: " + key, e);
        }
    }
}
