import request from '@/utils/request'

// ==================== 筛选条件配置（默认列 + 预设模板） ====================
// 后端按 JWT 自动取当前用户 user_id，接口无需显式传 userId

// ---- 默认全局筛选列（每用户唯一）----

/** 取我的默认列配置 */
export function getDefaultColumns() {
  return request({
    url: '/filter-preset/default-columns',
    method: 'get'
  })
}

/** 保存（新建或更新）我的默认列配置
 *  @param {string[]} columns 列名数组，如 ['GR','AC','SP']
 */
export function saveDefaultColumns(columns) {
  return request({
    url: '/filter-preset/default-columns',
    method: 'post',
    data: { columnsJson: JSON.stringify(columns) }
  })
}

// ---- 条件预设模板（每用户N条）----

/** 列出我的所有预设模板 */
export function listPresets() {
  return request({
    url: '/filter-preset/preset',
    method: 'get'
  })
}

/** 取单个预设详情 */
export function getPreset(id) {
  return request({
    url: `/filter-preset/preset/${id}`,
    method: 'get'
  })
}

/**
 * 新建或更新一个预设模板
 * @param {Object} preset { id?, name, scope, columnsJson?, filtersJson?, textFiltersJson? }
 *   - 有 id 表示更新，无 id 表示新建
 *   - columnsJson/filtersJson/textFiltersJson 传字符串化后的 JSON
 */
export function savePreset(preset) {
  return request({
    url: '/filter-preset/preset',
    method: 'post',
    data: preset
  })
}

/** 删除一个预设模板 */
export function deletePreset(id) {
  return request({
    url: `/filter-preset/preset/${id}`,
    method: 'delete'
  })
}
