package com.sellm.multimodal;

/**
 * 图像脱敏:出网前对含儿童面部的图片做不可逆处理(打码/模糊)。
 * 实现按 sellm.image-anon.provider 装配(默认 Noop 不改图;http 接外部 CV 打码服务)。
 */
public interface ImageAnonymizer {
    /** 返回脱敏后图片字节;null/空原样返回。失败应抛异常(绝不让原图出网)。 */
    byte[] sanitize(byte[] image);
}
