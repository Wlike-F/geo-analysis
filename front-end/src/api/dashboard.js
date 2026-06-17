import request from '@/utils/request'

export function getDashboardStats() {
  return request({
    url: '/dashboard/stats',
    method: 'get'
  })
}

export function getRecentFiles() {
  return request({
    url: '/dashboard/recentFiles',
    method: 'get'
  })
}
