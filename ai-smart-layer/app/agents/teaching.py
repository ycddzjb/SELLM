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


_LAYERED_SKELETON = (
    "【分层教学骨架范例(提炼自优秀教案,务必遵循)】\n"
    "1. 学情分析(必备):障碍特征与功能水平、优势能力与兴趣、挑战领域与支持需求;"
    "并按障碍程度分组说明(如 A组轻度/B组中度/C组重度,各组现有能力与差异)。\n"
    "2. 分层教学目标:同一主题对 A/B/C 组分别设可量化(SMART)目标,体现能力梯度。\n"
    "3. 分层学具与情境准备:各组适配的教具/提示卡/结构化情境。\n"
    "4. 教学过程(分步):每环节标注对不同层级的差异化任务与逐级递减的辅助(语言/视觉/肢体提示)。\n"
    "5. 分层评价:各组达标标准与数据化评估方式。\n"
)

# 输出格式约束:纯文本,禁用 markdown/html,适配 Word 排版
_FORMAT_RULE = (
    "【输出格式要求(严格遵守)】\n"
    "- 输出纯文本,严禁使用 Markdown 或 HTML 标记:不要出现 #、*、**、`、---、表格竖线 | 等符号。\n"
    "- 一级标题用中文方括号【】包裹(如【学情分析】);其下要点用 一、二、三 或 1. 2. 3. 编号;"
    "更细层级用 (1)(2) 或 · 圆点。\n"
    "- 段落之间用空行分隔,层次分明,便于直接导出为 Word 文档。\n"
)


def _teaching_prompt(task: str, requirement: str, opts: dict) -> str:
    """教学模块生成 prompt(训练方案/教案/课件/习题)。"""
    field = opts.get("field", "")          # 教学领域
    subject = opts.get("subject", "")      # 教学学科
    scene = opts.get("scene", "")          # 教学场景
    stage = opts.get("stage", "")          # 教学学段
    set_ = opts.get("specialEduType", "")  # 特教类型
    sched = opts.get("schedule", "")       # 课程安排
    klass = opts.get("classComposition", "")  # 班级构成(总/重/中/轻/正常)
    who = set_ or "特殊"
    ctx = (f"特教类型:{set_};教学学段:{stage};教学学科:{subject};课程安排:{sched};"
           f"教学领域:{field};教学场景:{scene};班级构成:{klass}。")
    if task == "plan":
        return (f"你是特殊教育训练方案专家。请面向「{who}」生成一份个别化训练方案。{ctx}要求:{requirement}\n"
                f"{_LAYERED_SKELETON}"
                f"请按【学情分析】【训练目标(分层)】【训练内容】【训练步骤(分步,分层)】【训练频次】【评估方式(分层)】结构输出。\n"
                f"{_FORMAT_RULE}")
    if task == "lesson":
        return (f"你是特殊教育备课专家。请面向「{who}」生成一份完整的分层教学教案。{ctx}要求:{requirement}\n"
                f"{_LAYERED_SKELETON}"
                f"请按【学情分析】【教学目标(分层)】【重难点】【教学准备(分层学具)】"
                f"【教学过程(分步,标注各层差异化任务与辅助)】【评价方式(分层)】结构输出,务必包含学情分析。\n"
                f"{_FORMAT_RULE}")
    if task == "courseware":
        return (f"你是特殊教育课件设计专家。请面向「{who}」生成教学课件内容。{ctx}要求:{requirement}\n"
                f"请按课件页面组织,每页给【标题】+【要点】+【配图建议】,适配该特教类型的认知特点。\n"
                f"{_FORMAT_RULE}")
    if task == "exercise":
        d = _disorders_text(opts)
        qtype = opts.get("questionType", "")   # 题型
        level = opts.get("difficulty", "")     # 难度
        ex_stage = opts.get("stage", "")       # 学段
        direction = opts.get("direction", "")  # 出题方向
        return (f"你是特殊教育训练题设计专家。请为{d}儿童生成训练题。"
                f"题型:{qtype};难度:{level};学段:{ex_stage};出题方向:{direction}。要求:{requirement}\n"
                f"请逐题给【题干】【答案】【解析】,语言简明、贴合该障碍类型;"
                f"如适合图文,请在题干后用文字标注【配图建议:...】以便图文适配。")
    return f"请基于以下要求生成特殊教育教学内容:{requirement}"


def _prompt_designer(task: str, requirement: str, opts: dict) -> str:
    """AIGC 出"提示词":据所选背景信息汇总,产一段高质量提示词(供教师编辑后再生成正文)。"""
    ctx = (f"特教类型:{opts.get('specialEduType','')};教学学段:{opts.get('stage','')};"
           f"教学学科:{opts.get('subject','')};课程安排:{opts.get('schedule','')};"
           f"教学领域:{opts.get('field','')};教学场景:{opts.get('scene','')};"
           f"班级构成:{opts.get('classComposition','')}。")
    kind = {"plan": "训练方案", "lesson": "教案", "courseware": "课件"}.get(task, "教学内容")
    extra = _LAYERED_SKELETON if task in ("plan", "lesson") else ""
    return (f"你是特殊教育教学提示词工程师。请基于以下背景信息,为生成「{kind}」撰写一段结构清晰、"
            f"要素完整的中文提示词(prompt),供后续直接喂给大模型生成正文。{ctx}背景:{requirement}\n"
            f"{extra}"
            f"请只输出可直接使用的提示词正文,须包含学情分析、分层教学目标、对象特点、内容要点与输出格式要求。")


def _courseware_from_lesson(lesson_content: str, opts: dict) -> str:
    d = _disorders_text(opts)
    return (f"你是特殊教育课件设计专家。请基于以下已定稿教案,为{d}儿童生成配套 PPT 课件内容。\n"
            f"教案正文:\n{lesson_content}\n"
            f"请按课件页面组织,每页给【标题】+【要点】+【配图建议】,适配该障碍类型认知特点。")


async def invoke_teaching(payload: dict) -> dict:
    task = payload.get("task", "lesson_plan")
    llm = get_llm()

    # 课件:基于已定稿教案正文生成(无状态草稿)
    if task == "courseware_from_lesson":
        opts = payload.get("options") or {}
        prompt = _courseware_from_lesson(payload.get("lessonContent", ""), opts)
        content = await llm.generate(prompt)
        return {"content": content, "mock": True}

    # 教学模块(新):带 requirement + options
    if task in ("plan", "lesson", "courseware", "exercise") and "requirement" in payload:
        opts = payload.get("options") or {}
        req = payload.get("requirement", "")
        # mode=prompt:AIGC 出"提示词"(草稿);否则生成正文
        if payload.get("mode") == "prompt":
            prompt = _prompt_designer(task, req, opts)
        else:
            prompt = _teaching_prompt(task, req, opts)
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

