import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app
from app.agents.teaching import invoke_teaching


@pytest.mark.asyncio
async def test_invoke_lesson_plan():
    out = await invoke_teaching({
        "task": "lesson_plan", "iepContent": "长期目标:共同注意",
        "disorderType": "ASD", "scene": "SCHOOL", "mode": "ONE_ON_ONE",
    })
    assert "content" in out and isinstance(out["content"], str) and out["content"]
    assert out.get("mock") is True


@pytest.mark.asyncio
async def test_invoke_courseware():
    out = await invoke_teaching({
        "task": "courseware", "lessonPlanContent": "教案正文",
        "disorderType": "ASD", "scene": "HOME", "mode": "GROUP",
    })
    assert "content" in out and isinstance(out["content"], str) and out["content"]


@pytest.mark.asyncio
async def test_teaching_invoke_endpoint():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.post("/v1/agents/teaching/invoke",
                                 json={"task": "lesson_plan", "iepContent": "[儿童1] 的目标",
                                       "disorderType": "ASD", "scene": "SCHOOL", "mode": "ONE_ON_ONE"})
    assert resp.status_code == 200
    assert "content" in resp.json()


@pytest.mark.asyncio
async def test_python_no_restore():
    # Python 不还原占位符
    out = await invoke_teaching({
        "task": "lesson_plan", "iepContent": "[儿童1] 的目标",
        "disorderType": "ASD", "scene": "SCHOOL", "mode": "ONE_ON_ONE",
    })
    assert isinstance(out["content"], str)  # 结构合规;无 restore 逻辑
