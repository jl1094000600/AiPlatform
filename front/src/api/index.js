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
    return api.get('/a2a/graph/agents/' + params.agentId + '/calls', { params })
  },
  getTrace(traceId) {
    return api.get('/monitor/traces/' + traceId)
  },
  getAgentGraph() {
    return api.get('/monitor/agent-graph')
  },
  getRealtimeData() {
    return api.get('/monitor/realtime')
  },
  getExecutionChain(params) {
    return api.get('/a2a/graph/executions/' + params.taskId)
  },
  exportGraph() {
    return api.post('/a2a/graph/export')
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
  },

  // Benchmark APIs
  getBenchmarkHistory() {
    return api.get('/benchmark/history')
  },
  uploadDataset(formData) {
    return api.post('/benchmark/dataset/upload', formData, {
      headers: { 'Content-Type': 'multipart/form-data' }
    })
  },
  generateSimData(data) {
    return api.post('/benchmark/simdata/generate', data)
  },
  startBenchmark(data) {
    return api.post('/benchmark/start', data)
  },
  getBenchmarkProgress(benchmarkId) {
    return api.get('/benchmark/progress/' + benchmarkId)
  },
  saveBenchmarkStandards(data) {
    return api.post('/benchmark/standards', data)
  },
  getBenchmarkResult(benchmarkId) {
    return api.get('/benchmark/result/' + benchmarkId)
  },
  exportBenchmarkReport(benchmarkId) {
    return api.get('/benchmark/export/' + benchmarkId, { responseType: 'blob' })
  },

  // Workflow APIs
  getWorkflows(params) {
    return api.get('/workflows', { params })
  },
  getWorkflow(id) {
    return api.get('/workflows/' + id)
  },
  createWorkflow(data) {
    return api.post('/workflows', data)
  },
  updateWorkflow(id, data) {
    return api.put('/workflows/' + id, data)
  },
  deleteWorkflow(id) {
    return api.delete('/workflows/' + id)
  },
  triggerWorkflow(id) {
    return api.post('/workflows/' + id + '/trigger')
  },
  getWorkflowExecutions(workflowId) {
    return api.get('/workflows/' + workflowId + '/executions')
  },
  getAllWorkflowExecutions() {
    return api.get('/workflows/executions')
  }
}
