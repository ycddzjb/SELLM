import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app
from app.agents.research import invoke_research


@pytest.mark.asyncio
async def test_invoke_research():
    out = await invoke_research({"topic": "特殊教育融合班级师资配置研究"})
    assert "content" in out and isinstance(out["content"], str) and out["content"]
    assert out.get("mock") is True


@pytest.mark.asyncio
async def test_research_invoke_endpoint():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.post("/v1/agents/research/invoke", json={"topic": "[学校1] 的融合教育研究"})
    assert resp.status_code == 200
    assert "content" in resp.json()


@pytest.mark.asyncio
async def test_python_no_restore():
    out = await invoke_research({"topic": "[学校1] 研究"})
    assert isinstance(out["content"], str)  # 无 restore 逻辑
