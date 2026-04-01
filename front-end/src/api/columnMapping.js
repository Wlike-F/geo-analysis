import request from '@/utils/request'

export function getColumnMappingPage(params) {
  return request({
    url: '/api/column-mapping/page',
    method: 'get',
    params
  })
}

export function getAllColumnMappings() {
  return request({
    url: '/api/column-mapping/list',
    method: 'get'
  })
}

export function addColumnMapping(data) {
  return request({
    url: '/api/column-mapping',
    method: 'post',
    data
  })
}

export function updateColumnMapping(data) {
  return request({
    url: '/api/column-mapping',
    method: 'put',
    data
  })
}

export function deleteColumnMapping(id) {
  return request({
    url: `/api/column-mapping/${id}`,
    method: 'delete'
  })
}

export function batchDeleteColumnMapping(ids) {
  return request({
    url: '/api/column-mapping/batchDelete',
    method: 'post',
    data: ids
  })
}
