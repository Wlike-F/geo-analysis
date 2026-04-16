import request from '@/utils/request'

export function login(data) {
  return request({
    url: '/auth/login',
    method: 'post',
    data
  })
}

export function refreshToken(data) {
  return request({
    url: '/auth/refresh',
    method: 'post',
    data,
    skipAuthRefresh: true
  })
}

export function logout(data) {
  return request({
    url: '/auth/logout',
    method: 'post',
    data,
    skipAuthRefresh: true
  })
}

export function register(data) {
  return request({
    url: '/auth/register',
    method: 'post',
    data
  })
}
