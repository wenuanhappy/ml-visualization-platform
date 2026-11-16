import axios from 'axios'

const baseURL = import.meta.env.VITE_API_BASE_URL || ''

const service = axios.create({
  baseURL,
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

service.interceptors.response.use(
  response => {
    const res = response.data
    if (res && typeof res.code === 'number' && res.code !== 200) {
      return Promise.reject(new Error(res.message || '请求失败'))
    }
    return res
  },
  error => Promise.reject(error)
)

export default service
