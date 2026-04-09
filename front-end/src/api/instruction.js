import request from '@/utils/request'

export function getInstructionList() {
  return request({
    url: '/instructions/list',
    method: 'get'
  })
}
