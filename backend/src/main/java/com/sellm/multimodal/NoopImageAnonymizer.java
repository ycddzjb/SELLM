package com.sellm.multimodal;

/**
 * 默认图像脱敏:原样返回,不改图。
 * 用于未配置外部打码服务的环境;真实 vision 默认也关闭,故默认链路无图像出网。
 */
public class NoopImageAnonymizer implements ImageAnonymizer {
    @Override
    public byte[] sanitize(byte[] image) {
        return image;
    }
}
