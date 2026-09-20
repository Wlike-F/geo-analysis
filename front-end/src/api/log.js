import request from '@/utils/request'

export function getLoginLogs(params) {
  return request({
    url: '/sys/log/login',
    method: 'get',
    params
  })
}

export function getAllLogs(params) {
  return request({
    url: '/sys/log/all',
    method: 'get',
    params
  })
}

export function getStorageStats() {
  return request({
    url: '/sys/log/storage',
    method: 'get'
  })
}

export function cleanupCache() {
  return request({
    url: '/sys/log/cleanup',
    method: 'post'
  })
}

export function getCleanupStatus() {
  return request({
    url: '/sys/log/cleanup/status',
    method: 'get'
  })
}

export function getRuntimeLogs(params) {
  return request({
    url: '/sys/log/runtime',
    method: 'get',
    params
  })
}
