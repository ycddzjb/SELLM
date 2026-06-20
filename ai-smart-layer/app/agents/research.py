"""科研助手 Agent — LangGraph 编排骨架(P0 空壳)"""


async def invoke_research(payload: dict) -> dict:
    return {"agent": "research", "mock": True, "input_keys": list(payload.keys())}
