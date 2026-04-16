import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'
import { getAccessToken, getRefreshToken, setTokenPair, clearTokenPair } from '@/utils/token'

const service = axios.create({
  baseURL: '/api',
  timeout: 300000
})

const authClient = axios.create({
  baseURL: '/api',
  timeout: 300000
})

let refreshPromise = null

function isUnauthorizedMessage(message) {
  if (!message) {
    return false
  }
  return ['鉴权失败', '未登录', 'token已经过期', '登录状态已失效', '请重新登录'].some(item => message.includes(item))
}

function redirectToLogin() {
  clearTokenPair()
  if (router.currentRoute.value.path !== '/login') {
    router.push('/login')
  }
}

async function requestNewAccessToken() {
  const refreshToken = getRefreshToken()
  if (!refreshToken) {
    throw new Error('登录状态已过期，请重新登录')
  }

  const response = await authClient.post('/auth/refresh', { refreshToken })
  const body = response.data
  if (!body || body.code !== 200 || !body.data) {
    throw new Error(body?.message || '刷新登录状态失败')
  }

  setTokenPair(body.data)
  return body.data.accessToken
}

async function handleUnauthorized(config, message) {
  if (!config || config.skipAuthRefresh) {
    return Promise.reject(new Error(message || '登录状态已失效'))
  }

  if (config._retry) {
    redirectToLogin()
    return Promise.reject(new Error(message || '登录状态已失效，请重新登录'))
  }

  config._retry = true

  try {
    refreshPromise = refreshPromise || requestNewAccessToken().finally(() => {
      refreshPromise = null
    })

    const newAccessToken = await refreshPromise
    config.headers = config.headers || {}
    config.headers.Authorization = 'Bearer ' + newAccessToken
    return service(config)
  } catch (error) {
    redirectToLogin()
    return Promise.reject(error)
  }
}

service.interceptors.request.use(
  config => {
    const token = getAccessToken()
    if (token) {
      config.headers.Authorization = 'Bearer ' + token
    }
    return config
  },
  error => Promise.reject(error)
)

service.interceptors.response.use(
  response => {
    if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') {
      return response.data
    }

    const res = response.data
    if (res.code === 200) {
      return res.data
    }

    if (res.code === 401 || isUnauthorizedMessage(res.message)) {
      return handleUnauthorized(response.config, res.message)
    }

    ElMessage.error(res.message || '操作失败')
    return Promise.reject(new Error(res.message || 'Error'))
  },
  error => {
    const status = error.response?.status
    const bizCode = error.response?.data?.code
    const message = error.response?.data?.message || error.message

    if (status === 401 || bizCode === 401 || isUnauthorizedMessage(message)) {
      return handleUnauthorized(error.config, message)
    }

    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default service
