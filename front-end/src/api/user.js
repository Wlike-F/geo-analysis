import request from '@/utils/request'

export function getUserInfo() {
  return request({
    url: '/user/info',
    method: 'get'
  })
}

export function updateProfile(data) {
  return request({
    url: '/user/updateProfile',
    method: 'post',
    data
  })
}

export function updatePwd(data) {
  return request({
    url: '/user/updatePwd',
    method: 'post',
    data
  })
}

export function getUserPage(params) {
  return request({
    url: '/user/page',
    method: 'get',
    params
  })
}

export function addUser(data) {
  return request({
    url: '/user/add',
    method: 'post',
    data
  })
}

export function updateUser(data) {
  return request({
    url: '/user/update',
    method: 'post',
    data
  })
}

export function deleteUser(id) {
  return request({
    url: `/user/delete/${id}`,
    method: 'delete'
  })
}

export function updateSettings(data) {
  return request({
    url: '/user/updateSettings',
    method: 'post',
    data
  })
}
