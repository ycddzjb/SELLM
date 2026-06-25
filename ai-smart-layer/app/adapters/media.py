"""文生媒体适配器 — 可切换(mock/openai),默认 mock 不外联。

镜像 llm.py 范式:抽象基类 + Mock(默认零外联确定性产物)+ 真实实现(仅显式配 key 时启用,
发请求抽为 _send() 便于测试子类化注入假响应、不真连网)。

红线:只接收已脱敏文本 prompt(Java 出网前脱敏),不接触明文/图片,无新 PII 出网面。
真实生成器出网即代表配置方自担合规(同后端 MultimodalProperties 注释惯例)。
"""
import base64
import struct

from app.config import settings

# 1x1 透明 PNG(最小合法图像,确定性占位,零外联)
_PLACEHOLDER_PNG = base64.b64decode(
    "iVBORw0KGgoAAAANSUhEUgAAAAEAAAABCAQAAAC1HAwCAAAAC0lEQVR42mNk+M9QDwADhgGAWjR9awAAAABJRU5ErkJggg=="
)


def _placeholder_wav() -> bytes:
    """生成极简静音 WAV(44 字节头 + 0 采样),最小合法音频占位。"""
    sample_rate = 8000
    data = b""
    header = b"RIFF"
    header += struct.pack("<I", 36 + len(data))
    header += b"WAVE"
    header += b"fmt "
    header += struct.pack("<IHHIIHH", 16, 1, 1, sample_rate, sample_rate, 1, 8)
    header += b"data"
    header += struct.pack("<I", len(data))
    return header + data


def _placeholder_mp4() -> bytes:
    """最小合法 MP4 容器(ftyp box,无媒体轨),确定性占位,零外联。"""
    # ftyp box: size(4) + 'ftyp' + major_brand 'isom' + minor_version(4) + compatible 'isom''mp42'
    brand = b"isom"
    body = brand + struct.pack(">I", 0x200) + b"isom" + b"mp42"
    box = struct.pack(">I", 8 + len(body)) + b"ftyp" + body
    return box


class MediaGenerator:
    """文生媒体抽象。generate 返回 {media_b64, mime_type, ext} 或 None(纯文本类)。"""

    async def generate(self, asset_type: str, prompt: str) -> dict | None:
        raise NotImplementedError


class MockMediaGenerator(MediaGenerator):
    """默认媒体生成器:按类型产最小合法占位二进制,零外联、确定性。用于 dev/test 与未配 key 环境。"""

    async def generate(self, asset_type: str, prompt: str) -> dict | None:
        t = (asset_type or "").upper()
        if t in ("IMAGE", "PICTUREBOOK"):
            return {
                "media_b64": base64.b64encode(_PLACEHOLDER_PNG).decode("ascii"),
                "mime_type": "image/png",
                "ext": "png",
            }
        if t == "AUDIO":
            return {
                "media_b64": base64.b64encode(_placeholder_wav()).decode("ascii"),
                "mime_type": "audio/wav",
                "ext": "wav",
            }
        if t == "VIDEO":
            return {
                "media_b64": base64.b64encode(_placeholder_mp4()).decode("ascii"),
                "mime_type": "video/mp4",
                "ext": "mp4",
            }
        return None


class OpenAiImageGenerator(MediaGenerator):
    """OpenAI 兼容图像生成(provider=openai 且配 key 时启用)。仅图像;音视频仍降级 mock/文本。

    ⚠️ 出网会把(已脱敏的)prompt 发往第三方 —— 合规风险由配置方承担。
    发请求抽为 _send() 便于测试子类化注入假响应,不真连网。
    """

    async def generate(self, asset_type: str, prompt: str) -> dict | None:
        t = (asset_type or "").upper()
        if t not in ("IMAGE", "PICTUREBOOK"):
            return None  # 非图像类暂不真实生成,上层存文本/mock
        resp = await self._send({
            "model": settings.media_model,
            "prompt": prompt,
            "n": 1,
            "size": "512x512",
            "response_format": "b64_json",
        })
        b64 = resp.get("data", [{}])[0].get("b64_json", "")
        if not b64:
            return None
        return {"media_b64": b64, "mime_type": "image/png", "ext": "png"}

    async def _send(self, payload: dict) -> dict:
        """实际发请求(OpenAI 兼容图像生成 API)。强制 HTTP/1.1(部分网关与 HTTP/2 协商卡死)。

        测试子类覆盖注入假响应,不真连网。
        """
        import httpx

        url = settings.media_base_url.rstrip("/") + "/v1/images/generations"
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {settings.media_api_key}",
        }
        async with httpx.AsyncClient(http1=True, http2=False,
                                     timeout=settings.media_timeout) as client:
            resp = await client.post(url, json=payload, headers=headers)
            if resp.status_code // 100 != 2:
                raise RuntimeError(f"图像生成返回非 2xx: {resp.status_code}")
            return resp.json()


class WanxImageGenerator(MediaGenerator):
    """阿里云通义万相文生图(provider=wanx 且配 key 时启用)。仅图像;其他类型返 None。

    DashScope 专有异步 API:提交任务(X-DashScope-Async)→ 轮询 task → 取图 URL → 下载转 b64。
    四步各抽为可覆写方法,测试子类注入假响应、不真连网。强制 HTTP/1.1。
    ⚠️ 出网会把(已脱敏的)prompt 发往第三方 —— 合规风险由配置方承担。
    """

    SUBMIT_PATH = "/api/v1/services/aigc/text2image/image-synthesis"
    TASK_PATH = "/api/v1/tasks/"

    async def generate(self, asset_type: str, prompt: str) -> dict | None:
        if (asset_type or "").upper() not in ("IMAGE", "PICTUREBOOK"):
            return None
        task_id = await self._submit(prompt)
        if not task_id:
            return None
        for _ in range(settings.media_max_polls):
            status = await self._poll(task_id)
            state = (status.get("output", {}).get("task_status", "") or "").upper()
            if state == "SUCCEEDED":
                results = status.get("output", {}).get("results", [])
                url = results[0].get("url", "") if results else ""
                if not url:
                    return None
                data = await self._download(url)
                return {
                    "media_b64": base64.b64encode(data).decode("ascii"),
                    "mime_type": "image/png",
                    "ext": "png",
                }
            if state in ("FAILED", "CANCELED", "UNKNOWN"):
                raise RuntimeError(f"万相生成失败: {status.get('output', {}).get('message', state)}")
            await self._sleep(settings.media_poll_interval)
        raise RuntimeError("万相生成超时")

    async def _submit(self, prompt: str) -> str:
        """提交异步文生图任务,返回 task_id。"""
        import httpx

        url = settings.media_base_url.rstrip("/") + self.SUBMIT_PATH
        headers = {
            "Content-Type": "application/json",
            "Authorization": f"Bearer {settings.media_api_key}",
            "X-DashScope-Async": "enable",
        }
        payload = {
            "model": settings.media_model,
            "input": {"prompt": prompt},
            "parameters": {"size": settings.media_size, "n": 1},
        }
        async with httpx.AsyncClient(http1=True, http2=False,
                                     timeout=settings.media_timeout) as client:
            resp = await client.post(url, json=payload, headers=headers)
            if resp.status_code // 100 != 2:
                raise RuntimeError(f"万相任务提交返回非 2xx: {resp.status_code}")
            return resp.json().get("output", {}).get("task_id", "")

    async def _poll(self, task_id: str) -> dict:
        """查询任务状态(返回完整 DashScope 响应体)。"""
        import httpx

        url = settings.media_base_url.rstrip("/") + self.TASK_PATH + task_id
        headers = {"Authorization": f"Bearer {settings.media_api_key}"}
        async with httpx.AsyncClient(http1=True, http2=False,
                                     timeout=settings.media_timeout) as client:
            resp = await client.get(url, headers=headers)
            if resp.status_code // 100 != 2:
                raise RuntimeError(f"万相任务轮询返回非 2xx: {resp.status_code}")
            return resp.json()

    async def _download(self, url: str) -> bytes:
        """下载产物图片字节。"""
        import httpx

        async with httpx.AsyncClient(http1=True, http2=False,
                                     timeout=settings.media_timeout) as client:
            resp = await client.get(url)
            if resp.status_code // 100 != 2:
                raise RuntimeError(f"万相产物下载返回非 2xx: {resp.status_code}")
            return resp.content

    async def _sleep(self, seconds: float) -> None:
        """轮询间隔(抽出便于测试覆盖为 no-op)。"""
        import asyncio
        await asyncio.sleep(seconds)


class OpenAiVideoGenerator(MediaGenerator):
    """文生视频(provider=openai 且配 key 时启用)。仅 VIDEO;其他类型返 None(上层降级)。

    真实文生视频通常异步:提交任务 → 轮询状态 → 下载产物。三步各抽为可覆写方法,
    测试子类注入假响应、不真连网。强制 HTTP/1.1。
    ⚠️ 出网会把(已脱敏的)prompt 发往第三方 —— 合规风险由配置方承担。
    """

    async def generate(self, asset_type: str, prompt: str) -> dict | None:
        if (asset_type or "").upper() != "VIDEO":
            return None
        job_id = await self._submit(prompt)
        # 轮询直到完成 / 失败 / 超时
        for _ in range(settings.video_max_polls):
            status = await self._poll(job_id)
            state = status.get("status", "")
            if state in ("succeeded", "completed", "success"):
                url = status.get("url") or status.get("video_url", "")
                b64 = status.get("b64_json", "")
                if b64:
                    return {"media_b64": b64, "mime_type": "video/mp4", "ext": "mp4"}
                if url:
                    data = await self._download(url)
                    return {
                        "media_b64": base64.b64encode(data).decode("ascii"),
                        "mime_type": "video/mp4",
                        "ext": "mp4",
                    }
                return None
            if state in ("failed", "error", "cancelled"):
                raise RuntimeError(f"视频生成失败: {status.get('error', state)}")
            await self._sleep(settings.video_poll_interval)
        raise RuntimeError("视频生成超时")

    async def _submit(self, prompt: str) -> str:
        """提交生成任务,返回 job_id。"""
        import httpx

        url = settings.video_base_url.rstrip("/") + "/v1/videos/generations"
        headers = {"Content-Type": "application/json",
                   "Authorization": f"Bearer {settings.video_api_key}"}
        payload = {"model": settings.video_model, "prompt": prompt}
        async with httpx.AsyncClient(http1=True, http2=False,
                                     timeout=settings.video_timeout) as client:
            resp = await client.post(url, json=payload, headers=headers)
            if resp.status_code // 100 != 2:
                raise RuntimeError(f"视频任务提交返回非 2xx: {resp.status_code}")
            return resp.json().get("id", "")

    async def _poll(self, job_id: str) -> dict:
        """查询任务状态。"""
        import httpx

        url = settings.video_base_url.rstrip("/") + f"/v1/videos/generations/{job_id}"
        headers = {"Authorization": f"Bearer {settings.video_api_key}"}
        async with httpx.AsyncClient(http1=True, http2=False,
                                     timeout=settings.video_timeout) as client:
            resp = await client.get(url, headers=headers)
            if resp.status_code // 100 != 2:
                raise RuntimeError(f"视频任务轮询返回非 2xx: {resp.status_code}")
            return resp.json()

    async def _download(self, url: str) -> bytes:
        """下载产物字节。"""
        import httpx

        async with httpx.AsyncClient(http1=True, http2=False,
                                     timeout=settings.video_timeout) as client:
            resp = await client.get(url)
            if resp.status_code // 100 != 2:
                raise RuntimeError(f"视频产物下载返回非 2xx: {resp.status_code}")
            return resp.content

    async def _sleep(self, seconds: float) -> None:
        """轮询间隔等待(抽出便于测试覆盖为 no-op)。"""
        import asyncio
        await asyncio.sleep(seconds)


class CompositeMediaGenerator(MediaGenerator):
    """按类型分发到不同真实适配器:图像→image_gen,视频→video_gen,其余→fallback(mock)。"""

    def __init__(self, image_gen=None, video_gen=None, fallback=None):
        self.image_gen = image_gen
        self.video_gen = video_gen
        self.fallback = fallback or MockMediaGenerator()

    async def generate(self, asset_type: str, prompt: str) -> dict | None:
        t = (asset_type or "").upper()
        if t in ("IMAGE", "PICTUREBOOK") and self.image_gen is not None:
            return await self.image_gen.generate(asset_type, prompt)
        if t == "VIDEO" and self.video_gen is not None:
            return await self.video_gen.generate(asset_type, prompt)
        # 音频等无真实适配器的类型走 mock 占位
        return await self.fallback.generate(asset_type, prompt)


def get_media_generator() -> MediaGenerator:
    if settings.media_provider == "wanx" and settings.media_api_key:
        image_gen = WanxImageGenerator()
    elif settings.media_provider == "openai" and settings.media_api_key:
        image_gen = OpenAiImageGenerator()
    else:
        image_gen = None
    video_gen = (OpenAiVideoGenerator()
                 if settings.video_provider == "openai" and settings.video_api_key else None)
    if image_gen is None and video_gen is None:
        return MockMediaGenerator()   # 全 mock(默认零外联)
    return CompositeMediaGenerator(image_gen=image_gen, video_gen=video_gen)
