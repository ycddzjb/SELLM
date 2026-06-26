"""教学训练 Agent。
教学(新):task=plan/lesson/courseware/exercise,带 requirement + options
  (残障类型多选/学段/学科/领域/场景/形式/题型等)。
训练(旧IEP,保留兜底):task=lesson_plan/courseware,基于 IEP/教案。
mock LLM 默认不外联;Python 只见脱敏文本,不还原。"""
from app.adapters.llm import get_llm

_DISORDER = {
    "ASD": "孤独症", "DEVELOPMENTAL_DELAY": "发育迟缓", "INTELLECTUAL": "智力障碍",
    "LANGUAGE": "语言障碍", "SENSORY_INTEGRATION": "感统失调", "CEREBRAL_PALSY": "脑瘫",
    "ADHD": "注意缺陷多动障碍", "HEARING_VISION": "听视障",
}


def _disorder_label(code: str) -> str:
    return _DISORDER.get(code, code or "特殊")


def _disorders_text(opts: dict) -> str:
    """残障类型:新版 disorderTypes 为数组(label 或 code),兼容旧 disorderType 单值。"""
    vals = opts.get("disorderTypes")
    if isinstance(vals, list) and vals:
        return "、".join(_disorder_label(v) for v in vals)
    return _disorder_label(opts.get("disorderType", ""))


def _teaching_prompt(task: str, requirement: str, opts: dict) -> str:
    """教学模块生成 prompt(训练方案/教案/课件/习题)。"""
    d = _disorders_text(opts)
    field = opts.get("field", "")          # 教学领域
    form = opts.get("form", "")            # 教学形式(一对一/集体)
    subject = opts.get("subject", "")      # 教学学科
    scene = opts.get("scene", "")          # 教学场景(家庭/机构/学校)
    stage = opts.get("stage", "")          # 教学学段
    ctx = (f"残障类型:{d};教学学段:{stage};教学学科:{subject};"
           f"教学领域:{field};教学场景:{scene};教学形式:{form}。")
    if task == "plan":
        return (f"你是特殊教育训练方案专家。请为{d}儿童生成一份个别化训练方案。{ctx}要求:{requirement}\n"
                f"请按【训练目标】【训练内容】【训练步骤(分步)】【训练频次】【评估方式】结构输出。")
    if task == "lesson":
        return (f"你是特殊教育备课专家。请为{d}儿童生成一份完整教案。{ctx}要求:{requirement}\n"
                f"请按【教学目标】【重难点】【教学准备】【教学过程(分步)】【评价方式】结构输出。")
    if task == "courseware":
        return (f"你是特殊教育课件设计专家。请为{d}儿童生成教学课件内容。{ctx}要求:{requirement}\n"
                f"请按课件页面组织,每页给【标题】+【要点】+【配图建议】,适配该障碍类型的认知特点。")
    if task == "exercise":
        qtype = opts.get("questionType", "")   # 题型
        level = opts.get("difficulty", "")     # 难度
        ex_stage = opts.get("stage", "")       # 学段
        direction = opts.get("direction", "")  # 出题方向
        return (f"你是特殊教育训练题设计专家。请为{d}儿童生成训练题。"
                f"题型:{qtype};难度:{level};学段:{ex_stage};出题方向:{direction}。要求:{requirement}\n"
                f"请逐题给【题干】【答案】【解析】,语言简明、贴合该障碍类型;"
                f"如适合图文,请在题干后用文字标注【配图建议:...】以便图文适配。")
    return f"请基于以下要求生成特殊教育教学内容:{requirement}"


async def invoke_teaching(payload: dict) -> dict:
    task = payload.get("task", "lesson_plan")
    llm = get_llm()

    # 教学模块(新):带 requirement + options
    if task in ("plan", "lesson", "courseware", "exercise") and "requirement" in payload:
        opts = payload.get("options") or {}
        prompt = _teaching_prompt(task, payload.get("requirement", ""), opts)
        content = await llm.generate(prompt)
        return {"content": content, "mock": True}

    # 训练模块(旧):基于 IEP/教案
    disorder = payload.get("disorderType", "")
    scene = payload.get("scene", "")
    mode = payload.get("mode", "")
    if task == "courseware":
        source = payload.get("lessonPlanContent", "")
        prompt = (
            f"你是特殊教育课件设计助手。基于以下教案,为 {disorder} 儿童设计适配课件大纲"
            f"(场景 {scene},{mode})。教案:\n{source}\n课件大纲:"
        )
    else:
        source = payload.get("iepContent", "")
        prompt = (
            f"你是特殊教育备课助手。基于以下 IEP,为 {disorder} 儿童设计分层教案"
            f"(场景 {scene},{mode})。IEP:\n{source}\n分层教案:"
        )
    content = await llm.generate(prompt)
    return {"content": content, "mock": True}

