package com.sellm.common.crypto;

public interface FieldCipher {
    /** 加密明文,返回 Base64 编码的密文(含 IV) */
    String encrypt(String plaintext);

    /** 解密 Base64 密文,返回明文 */
    String decrypt(String ciphertext);
}
