"""RAG 检索管线骨架(P0 空壳;P2 阶段接入 Milvus + LlamaIndex)"""
from typing import List


async def retrieve(query: str, collection: str = "kb_special_edu", top_k: int = 5) -> List[dict]:
    """P0: 返回空列表 mock;真实实现将调用 Milvus 向量检索 + rerank。"""
    return []
