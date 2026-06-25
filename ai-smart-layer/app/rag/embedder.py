"""嵌入适配器:接口 + Mock(确定性伪向量,不外联)。真实嵌入为后续可切换实现。"""
import hashlib
from typing import List, Protocol


class Embedder(Protocol):
    def embed(self, text: str) -> List[float]: ...


class MockEmbedder:
    """用文本 hash 生成确定性伪向量(固定 8 维),供测试与骨架,不调外部模型。"""
    DIM = 8

    def embed(self, text: str) -> List[float]:
        h = hashlib.sha256((text or "").encode("utf-8")).digest()
        # 取前 DIM 字节归一化到 [0,1)
        return [h[i] / 255.0 for i in range(self.DIM)]
