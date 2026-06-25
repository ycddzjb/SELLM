package com.sellm.storage;

/** 对象存储抽象:评估媒体落盘/取回。实现按 sellm.minio.provider 装配。 */
public interface ObjectStorage {
    /** 存对象,返回对象 key(调用方据此回查)。 */
    String put(String key, byte[] data, String contentType);

    /** 按 key 取回字节;不存在返回 null。 */
    byte[] get(String key);

    /** 返回可访问该对象的引用(MinIO 为预签名 URL;noop 为本地路径标识)。 */
    String reference(String key);
}
