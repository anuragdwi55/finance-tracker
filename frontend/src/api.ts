// // src/api.ts
// import axios from 'axios'
//
// const api = axios.create({ baseURL: '/api' })
//
// // Attach token on every request
// api.interceptors.request.use((config) => {
//   const token = localStorage.getItem('token')
//   if (token) {
//     config.headers = config.headers || {}
//     config.headers.Authorization = `Bearer ${token}`
//   }
//   return config
// })
//
// let redirecting = false
//
// // helper: does this URL point to an auth endpoint?
// const isAuthPath = (url?: string) => {
//   if (!url) return false
//   // url might be relative ('/auth/login') or absolute ('http://.../auth/login')
//   return url.includes('/auth/')
// }
//
// api.interceptors.response.use(
//   res => res,
//   err => {
//     const status = err?.response?.status
//     const url = err?.config?.url as string | undefined
//     const method = (err?.config?.method || '').toLowerCase()
//
//     // do not redirect for auth endpoints or OPTIONS preflights
//     const shouldRedirect =
//       (status === 401 || status === 403) &&
//       !isAuthPath(url) &&
//       method !== 'options'
//
//     if (shouldRedirect && !redirecting) {
//       redirecting = true
//       const here = window.location.pathname + window.location.search
//       localStorage.removeItem('token')
//       window.location.replace('/login?next=' + encodeURIComponent(here))
//     }
//     return Promise.reject(err)
//   }
// )
//
// export default api

// frontend/src/api.ts
import axios from 'axios'

// Vercel/Render will inject this at build time
const API_BASE = import.meta.env.VITE_API_BASE || '/api'  // '/api' for local proxy

const api = axios.create({ baseURL: API_BASE })

api.interceptors.request.use((config) => {
  const token = localStorage.getItem('token')
  if (token) {
    api.interceptors.request.use((config) => {
      const token = localStorage.getItem('token')

      if (token) {
        config.headers = {
          ...(config.headers ?? {}),
          Authorization: `Bearer ${token}`,
        }
      }

      return config
    })

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

