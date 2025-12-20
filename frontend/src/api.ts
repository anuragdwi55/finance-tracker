import axios, {
  AxiosError,
  AxiosHeaders,
  InternalAxiosRequestConfig,
} from 'axios'

const api = axios.create({
  baseURL: import.meta.env.VITE_API_BASE ?? '/api',
})

// Attach token safely (Axios v1: headers is AxiosHeaders)
api.interceptors.request.use((config: InternalAxiosRequestConfig) => {
  const token = localStorage.getItem('token')
  if (token) {
    if (!(config.headers instanceof AxiosHeaders)) {
      // wrap any existing headers into AxiosHeaders to satisfy TS
      config.headers = new AxiosHeaders(config.headers)
    }
    (config.headers as AxiosHeaders).set('Authorization', `Bearer ${token}`)
  }
  return config
})

let redirecting = false
api.interceptors.response.use(
  res => res,
  err => {
    const status = err?.response?.status
    if ((status === 401 || status === 403) && !redirecting) {
      redirecting = true
      const here = window.location.pathname + window.location.search
      localStorage.removeItem('token')
      window.location.replace('/login?next=' + encodeURIComponent(here))
    }
    return Promise.reject(err)
  }
)

export default api
