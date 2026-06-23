"""智能教具 Agent — 文生素材描述 + 媒体生成(mock LLM/媒体,不挂 RAG)。

Python 只见脱敏文本,不还原。文本描述走 LLM;媒体(图/音)走可切换 MediaGenerator(默认 mock 占位)。
返回 content(文本)+ 可选 media_b64/mime_type/ext(Java 解码落 ObjectStorage)。
"""
from app.adapters.llm import get_llm
from app.adapters.media import get_media_generator


async def invoke_aids(payload: dict) -> dict:
    asset_type = payload.get("type", "IMAGE")
    prompt = payload.get("prompt", "")
    descriptor = {
        "IMAGE": "教学插图",
        "PICTUREBOOK": "社交故事绘本",
        "VIDEO": "教学短视频脚本",
        "AUDIO": "听觉训练音频脚本",
    }.get(asset_type, "教学素材")
    llm_prompt = (
        f"你是特殊教育数字化教具设计助手。基于以下需求,生成一份 {descriptor} 的内容描述"
        f"(含主题、画面/情节要点、适配建议)。\n需求:{prompt}\n素材描述:"
    )
    llm = get_llm()
    content = await llm.generate(llm_prompt)

    result = {"content": content, "mock": True}

    # 媒体生成(默认 mock 产占位二进制;VIDEO 等返 None 则仅存文本描述)
    media = await get_media_generator().generate(asset_type, prompt)
    if media:
        result.update(media)  # media_b64 / mime_type / ext
    return result
