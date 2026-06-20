"""教学训练 Agent — LangGraph 编排骨架(P0 空壳)"""


async def invoke_teaching(payload: dict) -> dict:
    """P0: 返回 mock;P3 阶段实现 LangGraph 状态图。"""
    return {"agent": "teaching", "mock": True, "input_keys": list(payload.keys())}
