"""智能教具 Agent — LangGraph 编排骨架(P0 空壳)"""


async def invoke_aids(payload: dict) -> dict:
    return {"agent": "aids", "mock": True, "input_keys": list(payload.keys())}
