import axios from 'axios'

const client = axios.create({
  baseURL: '',
  timeout: 30000,
  headers: { 'Content-Type': 'application/json' }
})

client.interceptors.response.use(
  response => {
    const body = response.data
    if (body && typeof body.code === 'number' && body.code !== 200) {
      return Promise.reject(new Error(body.message || '请求失败'))
    }
    return body
  },
  error => Promise.reject(error)
)

export const getDatasets = () => client.get('/api/ml/datasets')
export const getDataset = id => client.get(`/api/ml/datasets/${id}`)
export const createSession = data => client.post('/api/ml/sessions', data)
export const stepSession = id => client.post(`/api/ml/sessions/${id}/step`)
export const resetSession = id => client.post(`/api/ml/sessions/${id}/reset`)
export const getProjection = (id, x, y) =>
  client.get(`/api/ml/sessions/${id}/projection`, { params: { x, y } })
