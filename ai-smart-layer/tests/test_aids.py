import pytest
from httpx import AsyncClient, ASGITransport
from app.main import app
from app.agents.aids import invoke_aids


@pytest.mark.asyncio
async def test_invoke_aids():
    out = await invoke_aids({"type": "PICTUREBOOK", "prompt": "认识情绪的绘本"})
    assert "content" in out and isinstance(out["content"], str) and out["content"]
    assert out.get("mock") is True


@pytest.mark.asyncio
async def test_aids_invoke_endpoint():
    transport = ASGITransport(app=app)
    async with AsyncClient(transport=transport, base_url="http://test") as client:
        resp = await client.post("/v1/agents/aids/invoke",
                                 json={"type": "IMAGE", "prompt": "为 [儿童1] 设计的视觉卡片"})
    assert resp.status_code == 200
    assert "content" in resp.json()


@pytest.mark.asyncio
async def test_python_no_restore():
    out = await invoke_aids({"type": "AUDIO", "prompt": "[学校1] 的听觉训练"})
    assert isinstance(out["content"], str)  # 无 restore 逻辑,占位符不还原


@pytest.mark.asyncio
async def test_image_returns_media():
    """IMAGE/PICTUREBOOK 默认 mock 产占位 PNG(media_b64 + mime_type + ext)。"""
    out = await invoke_aids({"type": "IMAGE", "prompt": "视觉卡片"})
    assert out.get("media_b64")
    assert out.get("mime_type") == "image/png"
    assert out.get("ext") == "png"


@pytest.mark.asyncio
async def test_audio_returns_wav():
    out = await invoke_aids({"type": "AUDIO", "prompt": "听觉训练"})
    assert out.get("mime_type") == "audio/wav"
    assert out.get("ext") == "wav"


@pytest.mark.asyncio
async def test_video_returns_mp4():
    """VIDEO 默认 mock 产占位 mp4(media_b64 + video/mp4 + ext)。"""
    out = await invoke_aids({"type": "VIDEO", "prompt": "教学短视频"})
    assert out.get("media_b64")
    assert out.get("mime_type") == "video/mp4"
    assert out.get("ext") == "mp4"


@pytest.mark.asyncio
async def test_openai_image_generator_parses_b64():
    """OpenAiImageGenerator.generate 经可覆写 _send 注入假响应,验证解析,不真连网。"""
    import base64
    from app.adapters.media import OpenAiImageGenerator

    fake_b64 = base64.b64encode(b"\x89PNG\r\n\x1a\n").decode("ascii")

    class StubImageGen(OpenAiImageGenerator):
        async def _send(self, payload):
            assert payload["prompt"]  # 收到(已脱敏)prompt
            assert payload["response_format"] == "b64_json"
            return {"data": [{"b64_json": fake_b64}]}

    out = await StubImageGen().generate("IMAGE", "[儿童1] 的视觉卡片")
    assert out["media_b64"] == fake_b64
    assert out["mime_type"] == "image/png"
    assert out["ext"] == "png"


@pytest.mark.asyncio
async def test_openai_image_generator_non_image_returns_none():
    """非图像类(AUDIO/VIDEO)真实图像生成器返 None(上层降级)。"""
    from app.adapters.media import OpenAiImageGenerator

    class StubImageGen(OpenAiImageGenerator):
        async def _send(self, payload):
            raise AssertionError("非图像类不应发请求")

    assert await StubImageGen().generate("AUDIO", "x") is None
    assert await StubImageGen().generate("VIDEO", "x") is None


@pytest.mark.asyncio
async def test_openai_video_submit_poll_download():
    """OpenAiVideoGenerator:提交→轮询(processing→succeeded)→下载,经 stub 注入不真连网。"""
    import base64
    from app.adapters.media import OpenAiVideoGenerator

    fake_mp4 = b"\x00\x00\x00\x18ftypisom"
    polls = []

    class StubVideoGen(OpenAiVideoGenerator):
        async def _submit(self, prompt):
            assert prompt  # 收到(已脱敏)prompt
            return "job_123"
        async def _poll(self, job_id):
            assert job_id == "job_123"
            polls.append(1)
            # 先 processing 一次,再 succeeded(带 url)
            return ({"status": "processing"} if len(polls) < 2
                    else {"status": "succeeded", "url": "https://fake/v.mp4"})
        async def _download(self, url):
            assert url == "https://fake/v.mp4"
            return fake_mp4
        async def _sleep(self, seconds):
            pass  # 测试不真等

    out = await StubVideoGen().generate("VIDEO", "[儿童1] 的教学视频")
    assert out["mime_type"] == "video/mp4"
    assert out["ext"] == "mp4"
    assert base64.b64decode(out["media_b64"]) == fake_mp4
    assert len(polls) == 2  # 轮询了两次


@pytest.mark.asyncio
async def test_openai_video_b64_inline():
    """轮询结果直接带 b64_json 时无需下载。"""
    import base64
    from app.adapters.media import OpenAiVideoGenerator

    fake_mp4 = b"\x00\x00\x00\x18ftypmp42"
    b64 = base64.b64encode(fake_mp4).decode("ascii")

    class StubVideoGen(OpenAiVideoGenerator):
        async def _submit(self, prompt): return "j"
        async def _poll(self, job_id): return {"status": "succeeded", "b64_json": b64}
        async def _download(self, url): raise AssertionError("有 b64 不应下载")
        async def _sleep(self, s): pass

    out = await StubVideoGen().generate("VIDEO", "x")
    assert out["media_b64"] == b64


@pytest.mark.asyncio
async def test_openai_video_failed_raises():
    """任务失败抛 RuntimeError(上层 AssetGenerationTask 会捕获 → 任务 FAILED)。"""
    import pytest as _pytest
    from app.adapters.media import OpenAiVideoGenerator

    class StubVideoGen(OpenAiVideoGenerator):
        async def _submit(self, prompt): return "j"
        async def _poll(self, job_id): return {"status": "failed", "error": "quota"}
        async def _sleep(self, s): pass

    with _pytest.raises(RuntimeError):
        await StubVideoGen().generate("VIDEO", "x")


@pytest.mark.asyncio
async def test_openai_video_non_video_returns_none():
    from app.adapters.media import OpenAiVideoGenerator

    class StubVideoGen(OpenAiVideoGenerator):
        async def _submit(self, prompt): raise AssertionError("非视频不应提交")

    assert await StubVideoGen().generate("IMAGE", "x") is None


@pytest.mark.asyncio
async def test_composite_dispatches_by_type():
    """CompositeMediaGenerator 按类型分发:图像→image_gen,视频→video_gen,音频→fallback(mock)。"""
    from app.adapters.media import CompositeMediaGenerator, MediaGenerator

    class FakeImage(MediaGenerator):
        async def generate(self, t, p): return {"media_b64": "IMG", "mime_type": "image/png", "ext": "png"}

    class FakeVideo(MediaGenerator):
        async def generate(self, t, p): return {"media_b64": "VID", "mime_type": "video/mp4", "ext": "mp4"}

    comp = CompositeMediaGenerator(image_gen=FakeImage(), video_gen=FakeVideo())
    assert (await comp.generate("IMAGE", "x"))["media_b64"] == "IMG"
    assert (await comp.generate("VIDEO", "x"))["media_b64"] == "VID"
    # AUDIO 无真实适配器 → fallback mock 占位 WAV
    audio = await comp.generate("AUDIO", "x")
    assert audio["mime_type"] == "audio/wav"



