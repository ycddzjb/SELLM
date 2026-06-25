package com.sellm.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.assertj.core.api.Assertions.*;

class StorageConfigTest {

    private final StorageConfig config = new StorageConfig();

    @Test
    void 默认provider为noop装配NoopStorage() {
        StorageProperties p = new StorageProperties(); // 默认 noop
        assertThat(config.objectStorage(p)).isInstanceOf(NoopObjectStorage.class);
    }

    @Test
    void provider为minio但endpoint空时回退Noop() {
        StorageProperties p = new StorageProperties();
        p.setProvider("minio");
        p.setEndpoint("");   // 无 endpoint → 回退本地
        assertThat(config.objectStorage(p)).isInstanceOf(NoopObjectStorage.class);
    }

    @Test
    void provider为minio且endpoint齐全时装配MinioStorage() {
        StorageProperties p = new StorageProperties();
        p.setProvider("minio");
        p.setEndpoint("http://localhost:9000");
        p.setAccessKey("ak");
        p.setSecretKey("sk");
        // 懒连接:构造不发起网络
        assertThat(config.objectStorage(p)).isInstanceOf(MinioObjectStorage.class);
    }

    @Test
    void Noop存取往返一致(@TempDir Path tmp) {
        NoopObjectStorage storage = new NoopObjectStorage(tmp.toString());
        byte[] data = "hello-media".getBytes();
        String key = storage.put("child/1/note.txt", data, "text/plain");
        assertThat(key).isEqualTo("child/1/note.txt");
        assertThat(storage.get(key)).isEqualTo(data);
        assertThat(storage.get("nope/missing")).isNull();
        assertThat(storage.reference(key)).startsWith("local://");
    }

    @Test
    void Noop拒绝目录穿越key(@TempDir Path tmp) {
        NoopObjectStorage storage = new NoopObjectStorage(tmp.toString());
        assertThatThrownBy(() -> storage.put("../escape.txt", new byte[]{1}, "x"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
