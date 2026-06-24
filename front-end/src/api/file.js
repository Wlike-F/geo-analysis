import request from '@/utils/request'

export function uploadTxtFile(data) {
  return request({
    url: '/file/upload',
    method: 'post',
    data
  })
}

export function scanServerPath(params) {
  return request({
    url: '/file/scan',
    method: 'post',
    params
  })
}

export function getPageData(data) {
  return request({
    url: '/data/page',
    method: 'post',
    data
  })
}

export function exportFilteredExcel(fileId, data) {
  return request({
    url: `/data/${fileId}/export`,
    method: 'post',
    data,
    responseType: 'blob'
  })
}

export function exportExcel(data) {
  return request({
    url: '/file/export',
    method: 'post',
    data,
    responseType: 'blob'
  })
}

export function exportBatchExcel(data) {
  return request({
    url: '/file/export-batch',
    method: 'post',
    data,
    responseType: 'blob'
  })
}

export function getFileList() {
  return request({
    url: '/file/list',
    method: 'get'
  })
}

export function getFilePage(params) {
  return request({
    url: '/file/page',
    method: 'get',
    params
  })
}

export function getFileParseReport(id) {
  return request({
    url: `/file/${id}/parse-report`,
    method: 'get'
  })
}

export function exportBatchExcelZip(data) {
  return request({
    url: '/file/export-batch-zip',
    method: 'post',
    data,
    responseType: 'blob'
  })
}
export function exportBatchZipStream(data) { return request({ url: '/data/export-batch-zip', method: 'post', data, responseType: 'blob' }) }

export function getEchartsData(fileId) {
  return request({
    url: `/data/echarts/${fileId}`,
    method: 'get'
  })
}

export function deleteFile(id) {
  return request({
    url: `/file/${id}`,
    method: 'delete'
  })
}

export function clearFiles() {
  return request({
    url: '/file/clear',
    method: 'delete'
  })
}

// ==================== 地质分层配置 ====================

export function getFileLayers(fileId) {
  return request({
    url: `/file/${fileId}/layers`,
    method: 'get'
  })
}

export function saveFileLayers(fileId, layers) {
  return request({
    url: `/file/${fileId}/layers`,
    method: 'post',
    data: layers
  })
}

export function deleteFileLayers(fileId) {
  return request({
    url: `/file/${fileId}/layers`,
    method: 'delete'
  })
}

export function copyLayersToFiles(sourceFileId, targetFileIds) {
  return request({
    url: `/file/${sourceFileId}/layers/copy`,
    method: 'post',
    data: targetFileIds
  })
}

export function importLayersFromExcel(fileId, formData, wellName) {
  const params = wellName ? { wellName } : {}
  return request({
    url: `/file/${fileId}/layers/import`,
    method: 'post',
    data: formData,
    params
  })
}

// ==================== 文本列筛选 ====================

export function getTextColumns(fileId) {
  return request({
    url: `/data/${fileId}/text-columns`,
    method: 'get'
  })
}

export function saveTextColumns(fileId, mapping) {
  return request({
    url: `/data/${fileId}/text-columns`,
    method: 'post',
    data: mapping
  })
}

export function getDistinctValues(fileId, column) {
  return request({
    url: `/data/${fileId}/distinct-values`,
    method: 'get',
    params: { column }
  })
}

