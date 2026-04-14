import axios from 'axios'
import router from '../router'

const api = axios.create({
  baseURL: '/api/v1',
  timeout: 10000
})

api.interceptors.request.use(config => {
  const token = localStorage.getItem('token')
  if (token) {
    config.headers.Authorization = 'Bearer ' + token
  }
  return config
})

// 响应拦截器：401时跳转登录页
api.interceptors.response.use(
  response => response,
  error => {
    if (error.response?.status === 401) {
      localStorage.removeItem('token')
      localStorage.removeItem('user')
      router.push('/login')
    }
    return Promise.reject(error)
  }
)

export default {
  // Agent APIs
  getAgents(params) {
    return api.get('/agents', { params })
  },
  getAgent(id) {
    return api.get('/agents/' + id)
  },
  createAgent(data) {
    return api.post('/agents', data)
  },
  updateAgent(id, data) {
    return api.put('/agents/' + id, data)
  },
  deleteAgent(id) {
    return api.delete('/agents/' + id)
  },
  publishAgent(id) {
    return api.post('/agents/' + id + '/publish')
  },
  offlineAgent(id) {
    return api.post('/agents/' + id + '/offline')
  },
  
  // Monitor APIs
  getCallRecords(params) {
    return api.get('/monitor/records', { params })
  },
  getTrace(traceId) {
    return api.get('/monitor/traces/' + traceId)
  },
  getAgentGraph() {
    return api.get('/monitor/agent-graph')
  },
  getExecutionChain(params) {
    return api.get('/monitor/execution-chain', { params })
  },
  exportGraph() {
    return api.get('/monitor/graph/export')
  },
  
  // Model APIs
  getModels(params) {
    return api.get('/models', { params })
  },
  getModel(id) {
    return api.get('/models/' + id)
  },
  createModel(data) {
    return api.post('/models', data)
  },
  updateModel(id, data) {
    return api.put('/models/' + id, data)
  },
  deleteModel(id) {
    return api.delete('/models/' + id)
  },
  
  // Auth APIs
  login(data) {
    return api.post('/auth/login', data)
  },
  register(data) {
    return api.post('/auth/register', data)
  }
}
