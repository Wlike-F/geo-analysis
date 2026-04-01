import axios from 'axios'
import { ElMessage } from 'element-plus'
import router from '@/router'

// 创建 axios 实例
const service = axios.create({
  baseURL: 'http://localhost:8080/api', // 设定后端基础URL，根据实际情况修改
  timeout: 300000, // 请求超时时间改为300秒(5分钟)，适配大文件或者批量文件上传
})

// request 拦截器
service.interceptors.request.use(
  config => {
    // 携带 token
    const token = localStorage.getItem('token')
    if (token) {
      config.headers['Authorization'] = 'Bearer ' + token
    }
    return config
  },
  error => {
    return Promise.reject(error)
  }
)

// response 拦截器
service.interceptors.response.use(
  response => {
    // 拦截 Blob 和 ArrayBuffer 类型的底层流（比如用于下载 Excel 文件），不要进行标准 JSON 转换！
    if (response.config.responseType === 'blob' || response.config.responseType === 'arraybuffer') {
      return response.data
    }

    const res = response.data
    // 如果是 200，说明成功
    if (res.code === 200) {
      return res.data
    } else {
      // 业务错误
      ElMessage.error(res.message || '操作失败')
      return Promise.reject(new Error(res.message || 'Error'))
    }
  },
  error => {
    console.error('err' + error) // for debug
    let message = error.message
    if (error.response?.data?.message) {
      message = error.response.data.message
    }
    ElMessage.error(message)
    return Promise.reject(error)
  }
)

export default service
