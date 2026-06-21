package com.sellm.storage;

import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;

import java.nio.file.Path;

import static org.junit.jupiter.api.Assertions.*;

class NoopObjectStorageTest {

    @Test
    void 存取往返一致(@TempDir Path tmp) {
        NoopObjectStorage storage = new NoopObjectStorage(tmp.toString());
        byte[] data = "hello".getBytes();
        String key = storage.put("k1.txt", data, "text/plain");
        assertNotNull(key);
        assertArrayEquals(data, storage.get(key));
    }

    @Test
    void 取不存在返回null(@TempDir Path tmp) {
        NoopObjectStorage storage = new NoopObjectStorage(tmp.toString());
        assertNull(storage.get("missing.txt"));
    }
}
