import axios, { AxiosRequestHeaders } from 'axios'

const API_BASE = import.meta.env.VITE_API_BASE || '/api'
const api = axios.create({ baseURL: API_BASE })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    // merge safely and keep the type happy
    const merged: AxiosRequestHeaders = {
      ...(config.headers as AxiosRequestHeaders || {}),
      Authorization: `Bearer ${token}`,
    }
    config.headers = merged
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
