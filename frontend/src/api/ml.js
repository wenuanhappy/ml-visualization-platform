import request from '@/utils/request'

/**
 * 机器学习模块接口（第 08 组）
 *
 * 与 ct-platform-v2 的 api 层写法一致：统一走 @/utils/request。
 * 这样本文件可以原样同步到主平台，不需要任何文本替换。
 */

export const getDatasets = () => request({ url: '/api/ml/datasets', method: 'get' })

export const getDataset = id => request({ url: `/api/ml/datasets/${id}`, method: 'get' })

export const createSession = data => request({ url: '/api/ml/sessions', method: 'post', data })

export const stepSession = id => request({ url: `/api/ml/sessions/${id}/step`, method: 'post' })

export const resetSession = id => request({ url: `/api/ml/sessions/${id}/reset`, method: 'post' })

export const getProjection = (id, x, y) =>
  request({ url: `/api/ml/sessions/${id}/projection`, method: 'get', params: { x, y } })

export const askAssistant = data => request({ url: '/api/ml/assistant', method: 'post', data })

export const uploadDataset = formData =>
  request({
    url: '/api/ml/datasets/upload',
    method: 'post',
    data: formData,
    headers: { 'Content-Type': 'multipart/form-data' }
  })

export const compareModels = data => request({ url: '/api/ml/comparison', method: 'post', data })

export const featureSelectionTrain = data =>
  request({ url: '/api/ml/feature-selection', method: 'post', data })
