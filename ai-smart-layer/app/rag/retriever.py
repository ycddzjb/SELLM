"""向量检索适配器:接口 + Mock(固定桩文档,不连 Milvus)。真实 Milvus 检索为后续可切换实现。"""
from typing import List, Protocol


class Retriever(Protocol):
    def retrieve(self, query_vec: List[float], collection: str, top_k: int) -> List[dict]: ...


class MockRetriever:
    """返回固定的 kb_policy 桩文档,供骨架与测试。真实实现将查 Milvus。"""

    _STUB_DOCS = [
        {"title": "融合教育推进指导意见",
         "source": "kb_policy/inclusive-edu-2020",
         "text": "推进残疾儿童少年随班就读,完善融合教育支持保障体系。"},
        {"title": "特殊教育提升计划",
         "source": "kb_policy/sped-plan",
         "text": "扩大特殊教育资源,提高特殊教育质量,健全经费投入机制。"},
    ]

    def retrieve(self, query_vec: List[float], collection: str = "kb_policy", top_k: int = 5) -> List[dict]:
        return self._STUB_DOCS[:top_k]
