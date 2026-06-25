// 教学内容导出:Word(docx) + 一键 PPT(pptxgenjs)。纯前端,不外联。
import { Document, Packer, Paragraph, HeadingLevel, TextRun } from 'docx'
import PptxGenJS from 'pptxgenjs'
import { saveAs } from 'file-saver'

/** 导出 Word:标题 + 正文(按行分段)。 */
export async function exportWord(title, content) {
  const lines = (content || '').split(/\n/)
  const children = [new Paragraph({ text: title || '教学内容', heading: HeadingLevel.HEADING_1 })]
  for (const line of lines) {
    children.push(new Paragraph({ children: [new TextRun(line)] }))
  }
  const doc = new Document({ sections: [{ children }] })
  const blob = await Packer.toBlob(doc)
  saveAs(blob, `${title || '教学内容'}.docx`)
}

/**
 * 一键 PPT:按"标题行"切页(以【...】或 # 或空行分隔的块),每块一页。
 * 简单启发式:遇到以【开头或较短的独立行视为页标题,其后内容为该页要点。
 */
export async function exportPpt(title, content) {
  const pptx = new PptxGenJS()
  // 封面
  const cover = pptx.addSlide()
  cover.addText(title || '教学内容', { x: 0.5, y: 2, w: 9, h: 1.5, fontSize: 32, bold: true, align: 'center' })

  const blocks = splitIntoSlides(content || '')
  for (const b of blocks) {
    const slide = pptx.addSlide()
    slide.addText(b.title, { x: 0.5, y: 0.4, w: 9, h: 0.8, fontSize: 24, bold: true, color: '2c3e50' })
    slide.addText(b.body, { x: 0.6, y: 1.4, w: 8.8, h: 5, fontSize: 16, valign: 'top' })
  }
  if (!blocks.length) {
    const s = pptx.addSlide()
    s.addText(content || '(空)', { x: 0.6, y: 1, w: 8.8, h: 5, fontSize: 16, valign: 'top' })
  }
  await pptx.writeFile({ fileName: `${title || '教学内容'}.pptx` })
}

function splitIntoSlides(content) {
  const lines = content.split(/\n/)
  const blocks = []
  let cur = null
  for (const raw of lines) {
    const line = raw.trim()
    if (!line) continue
    // 以【】或 # 开头,或形如 "一、" "1." 的行视为页标题
    const isHeading = /^[【#]/.test(line) || /^[一二三四五六七八九十]+[、.]/.test(line) || /^\d+[、.]/.test(line)
    if (isHeading) {
      if (cur) blocks.push(cur)
      cur = { title: line.replace(/^[#【]\s*/, '').replace(/】$/, ''), body: '' }
    } else if (cur) {
      cur.body += (cur.body ? '\n' : '') + line
    } else {
      cur = { title: '内容', body: line }
    }
  }
  if (cur) blocks.push(cur)
  return blocks
}
