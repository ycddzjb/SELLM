"""通用问答 Agent — LangGraph 编排骨架(P0 空壳)"""


async def invoke_qa(payload: dict) -> dict:
    return {"agent": "qa", "mock": True, "input_keys": list(payload.keys())}
