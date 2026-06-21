"""教学训练 Agent — 教案/课件生成(mock LLM,不挂 RAG)。Python 只见脱敏文本,不还原。"""
from app.adapters.llm import get_llm


async def invoke_teaching(payload: dict) -> dict:
    task = payload.get("task", "lesson_plan")
    disorder = payload.get("disorderType", "")
    scene = payload.get("scene", "")
    mode = payload.get("mode", "")
    if task == "courseware":
        source = payload.get("lessonPlanContent", "")
        prompt = (
            f"你是特殊教育课件设计助手。基于以下教案,为 {disorder} 儿童设计适配课件大纲"
            f"(场景 {scene},{mode})。教案:\n{source}\n课件大纲:"
        )
    else:
        source = payload.get("iepContent", "")
        prompt = (
            f"你是特殊教育备课助手。基于以下 IEP,为 {disorder} 儿童设计分层教案"
            f"(场景 {scene},{mode})。IEP:\n{source}\n分层教案:"
        )
    llm = get_llm()
    content = await llm.generate(prompt)
    return {"content": content, "mock": True}
