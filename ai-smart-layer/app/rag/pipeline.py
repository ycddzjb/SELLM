"""RAG 问答管线:嵌入 → 检索 → 拼上下文 → LLM 生成(带引用)。
mock 嵌入 + mock 检索 + mock LLM,默认不外联。Python 侧只见脱敏文本,不还原。"""
from typing import List

from app.rag.embedder import MockEmbedder
from app.rag.retriever import MockRetriever
from app.adapters.llm import get_llm

_embedder = MockEmbedder()
_retriever = MockRetriever()


async def retrieve(query: str, collection: str = "kb_special_edu", top_k: int = 5) -> List[dict]:
    """保留 P0 签名兼容:嵌入 + 检索,返回文档列表。"""
    vec = _embedder.embed(query)
    return _retriever.retrieve(vec, collection=collection, top_k=top_k)


async def answer_question(question: str, top_k: int = 5) -> dict:
    """完整 RAG:检索 kb_policy → 拼上下文 → LLM 生成 → {answer, sources}。"""
    docs = await retrieve(question, collection="kb_policy", top_k=top_k)
    context = "\n".join(f"- {d['title']}: {d['text']}" for d in docs)
    prompt = (
        "你是特殊教育政策与常识问答助手。基于以下资料回答问题,并在末尾标注引用来源。\n"
        f"资料:\n{context}\n\n问题:{question}\n回答:"
    )
    llm = get_llm()
    answer = await llm.generate(prompt)
    sources = [{"title": d["title"], "source": d["source"]} for d in docs]
    return {"answer": answer, "sources": sources}
