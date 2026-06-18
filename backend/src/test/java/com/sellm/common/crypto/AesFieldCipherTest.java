package com.sellm.common.crypto;

import org.junit.jupiter.api.Test;
import static org.assertj.core.api.Assertions.*;

class AesFieldCipherTest {

    // 32 字节(AES-256)测试密钥
    private final AesFieldCipher cipher =
        new AesFieldCipher("0123456789abcdef0123456789abcdef");

    @Test
    void 加密后密文不等于明文且能解密还原() {
        String plain = "小明";
        String enc = cipher.encrypt(plain);
        assertThat(enc).isNotEqualTo(plain);
        assertThat(cipher.decrypt(enc)).isEqualTo(plain);
    }

    @Test
    void 相同明文两次加密密文不同_因随机IV() {
        String plain = "张伟";
        assertThat(cipher.encrypt(plain)).isNotEqualTo(cipher.encrypt(plain));
    }

    @Test
    void 空字符串可加解密往返() {
        assertThat(cipher.decrypt(cipher.encrypt(""))).isEqualTo("");
    }

    @Test
    void 密钥长度非法则构造失败() {
        assertThatThrownBy(() -> new AesFieldCipher("tooshort"))
            .isInstanceOf(IllegalArgumentException.class);
    }
}
