import request from '@/utils/request'

export function getColumnMappingPage(params) {
  return request({
    url: '/column-mapping/page',
    method: 'get',
    params
  })
}

export function getAllColumnMappings() {
  return request({
    url: '/column-mapping/list',
    method: 'get'
  })
}

export function addColumnMapping(data) {
  return request({
    url: '/column-mapping',
    method: 'post',
    data
  })
}

export function updateColumnMapping(data) {
  return request({
    url: '/column-mapping',
    method: 'put',
    data
  })
}

export function deleteColumnMapping(id) {
  return request({
    url: `/column-mapping/${id}`,
    method: 'delete'
  })
}

export function batchDeleteColumnMapping(ids) {
  return request({
    url: '/column-mapping/batchDelete',
    method: 'post',
    data: ids
  })
}
