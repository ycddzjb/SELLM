"""通用问答 Agent — RAG 问答(mock 适配器)。"""
from app.rag.pipeline import answer_question


async def invoke_qa(payload: dict) -> dict:
    question = payload.get("question", "")
    top_k = int(payload.get("topK", 5))
    result = await answer_question(question, top_k=top_k)
    return {"answer": result["answer"], "sources": result["sources"], "mock": True}
