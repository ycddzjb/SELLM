"""科研助手 Agent — 课题申报书生成(mock LLM,不挂 RAG)。Python 只见脱敏文本,不还原。"""
from app.adapters.llm import get_llm


async def invoke_research(payload: dict) -> dict:
    topic = payload.get("topic", "")
    prompt = (
        "你是特殊教育科研助手。基于以下课题主题,撰写一份课题申报书草案"
        "(含研究背景、研究目标、研究方法、预期成果)。\n"
        f"课题主题:{topic}\n申报书草案:"
    )
    llm = get_llm()
    content = await llm.generate(prompt)
    return {"content": content, "mock": True}
