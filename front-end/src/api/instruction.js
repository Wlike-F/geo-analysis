import request from '@/utils/request'

export function getInstructionList() {
  return request({
    url: '/instruction/list',
    method: 'get'
  })
}
