// Excel 导入导出工具(纯前端,基于 SheetJS xlsx)。
import * as XLSX from 'xlsx'

/**
 * 导出表格为 .xlsx 下载。
 * @param {string} filename 文件名(不含扩展名)
 * @param {string} sheetName 工作表名
 * @param {Array<object>} rows 数据行(对象数组,key 为列)
 * @param {Array<{key:string,label:string}>} columns 列定义(顺序+表头)
 */
export function exportSheet(filename, sheetName, rows, columns) {
  const header = columns.map(c => c.label)
  const data = rows.map(r => columns.map(c => r[c.key] ?? ''))
  const aoa = [header, ...data]
  const ws = XLSX.utils.aoa_to_sheet(aoa)
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, sheetName || 'Sheet1')
  XLSX.writeFile(wb, `${filename}.xlsx`)
}

/**
 * 导出一个模板(仅表头 + 可选示例行)。
 * @param {string} filename
 * @param {Array<string>} headers 表头列名
 * @param {Array<Array<any>>} sampleRows 示例数据行(可空)
 */
export function exportTemplate(filename, headers, sampleRows = []) {
  const aoa = [headers, ...sampleRows]
  const ws = XLSX.utils.aoa_to_sheet(aoa)
  const wb = XLSX.utils.book_new()
  XLSX.utils.book_append_sheet(wb, ws, '模板')
  XLSX.writeFile(wb, `${filename}.xlsx`)
}

/**
 * 解析上传的 Excel 文件 → 行对象数组(以首行为表头 key)。
 * @param {File} file
 * @returns {Promise<Array<object>>}
 */
export function parseSheet(file) {
  return new Promise((resolve, reject) => {
    const reader = new FileReader()
    reader.onload = (e) => {
      try {
        const wb = XLSX.read(e.target.result, { type: 'array' })
        const ws = wb.Sheets[wb.SheetNames[0]]
        const rows = XLSX.utils.sheet_to_json(ws, { defval: '' })
        resolve(rows)
      } catch (err) { reject(err) }
    }
    reader.onerror = reject
    reader.readAsArrayBuffer(file)
  })
}
