"""LLM 适配器 — 可切换(mock/openai),默认 mock 不外联。"""
from app.config import settings


class MockLLM:
    async def generate(self, prompt: str) -> str:
        return f"[MOCK LLM] received prompt of {len(prompt)} chars"


class OpenAILLM:
    """OpenAI 兼容文本 LLM(provider=openai 且配 key 时启用)。强制 HTTP/1.1;
    发请求抽为 _send() 便于测试子类注入假响应、不真连网。
    ⚠️ 出网会把(已脱敏的)prompt 发往第三方 —— 合规由配置方承担。"""

    async def _send(self, payload: dict) -> dict:
        import httpx
        url = settings.ai_base_url.rstrip("/") + "/v1/chat/completions"
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {settings.ai_api_key}",
        }
        body = {"model": settings.ai_model, **payload}
        async with httpx.AsyncClient(http1=True, http2=False,
                                     timeout=settings.ai_timeout) as client:
            resp = await client.post(url, json=body, headers=headers)
            if resp.status_code // 100 != 2:
                raise RuntimeError(f"LLM 返回非 2xx: {resp.status_code}")
            return resp.json()

    async def generate(self, prompt: str) -> str:
        resp = await self._send({"messages": [{"role": "user", "content": prompt}]})
        return resp.get("choices", [{}])[0].get("message", {}).get("content", "")


def get_llm():
    if settings.ai_provider == "openai" and settings.ai_api_key:
        return OpenAILLM()
    return MockLLM()
