"""SELLM AI 智能层 — FastAPI 入口"""
from fastapi import FastAPI
from app.config import settings
from app.agents.qa import invoke_qa
from app.agents.teaching import invoke_teaching
from app.agents.research import invoke_research
from app.agents.aids import invoke_aids

app = FastAPI(title="SELLM AI Smart Layer", version="0.1.0")


@app.get("/health")
async def health():
    return {"service": "ai-smart-layer", "status": "UP", "ai_provider": settings.ai_provider}


@app.post("/v1/agents/qa/invoke")
async def qa_invoke(payload: dict):
    """通用问答 Agent RAG 问答(接收脱敏后问题)。"""
    return await invoke_qa(payload)


@app.post("/v1/agents/teaching/invoke")
async def teaching_invoke(payload: dict):
    """教学训练 Agent 教案/课件生成(接收脱敏后文本)。"""
    return await invoke_teaching(payload)


@app.post("/v1/agents/research/invoke")
async def research_invoke(payload: dict):
    """科研助手 Agent 课题申报书生成(接收脱敏后 topic)。"""
    return await invoke_research(payload)


@app.post("/v1/agents/aids/invoke")
async def aids_invoke(payload: dict):
    """智能教具 Agent 文生素材描述生成(接收脱敏后 type + prompt)。"""
    return await invoke_aids(payload)


@app.post("/v1/agents/{agent_name}/invoke")
async def invoke_agent(agent_name: str, payload: dict):
    """
    统一 Agent 调用入口(P0 空壳返回 mock)。
    Java 业务服务经此 endpoint 触发 LLM 编排。
    """
    return {
        "agent": agent_name,
        "status": "mock",
        "result": f"[MOCK] {agent_name} agent invoked with keys: {list(payload.keys())}",
    }
