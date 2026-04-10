import request from '@/utils/request'

export function getLoginLogs(params) {
  return request({
    url: '/sys/log/login',
    method: 'get',
    params
  })
}
