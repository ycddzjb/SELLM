"""LLM 适配器 — 可切换(mock/openai),默认 mock 不外联。"""
from app.config import settings


class MockLLM:
    async def generate(self, prompt: str) -> str:
        return f"[MOCK LLM] received prompt of {len(prompt)} chars"


class OpenAILLM:
    """P1+ 阶段实现;强制 HTTP/1.1,发请求抽为 _send() 便于测试。"""

    async def _send(self, payload: dict) -> dict:
        raise NotImplementedError("OpenAI adapter not implemented in P0")

    async def generate(self, prompt: str) -> str:
        resp = await self._send({"messages": [{"role": "user", "content": prompt}]})
        return resp.get("choices", [{}])[0].get("message", {}).get("content", "")


def get_llm():
    if settings.ai_provider == "openai":
        return OpenAILLM()
    return MockLLM()
