import React from 'react'
import { useNavigate, useSearchParams, Link } from 'react-router-dom'
import api from '../api'

export default function Login(){
  const nav = useNavigate()
  const [sp] = useSearchParams()
  const justRegistered = sp.get('registered') === '1'
  const next = sp.get('next') || '/'

  const [email, setEmail] = React.useState('')
  const [password, setPassword] = React.useState('')
  const [loading, setLoading] = React.useState(false)
  const [error, setError] = React.useState('')

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')
    setLoading(true)
    try {
      const { data } = await api.post('/auth/login', { email, password })
      localStorage.setItem('token', data.token)
      // go to the intended page if provided
      window.location.replace(next)
    } catch (err: any) {
      const status = err?.response?.status
      const msg = err?.response?.data?.message
      setError(msg || (status === 401 ? 'Invalid email or password.' : 'Login failed.'))
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="container" style={{ maxWidth: 420 }}>
      <h2 className="section-title">Login</h2>

      {justRegistered && (
        <div className="card" style={{ borderColor:'#16a34a', color:'#166534', marginBottom:12 }}>
          Account created — you can log in now.
        </div>
      )}

      {error && (
        <div className="card" style={{ borderColor:'#dc2626', color:'#dc2626', marginBottom:12 }}>
          {error}
        </div>
      )}

      <form className="card space-y-12" onSubmit={submit}>
        <div>
          <label className="muted">Email</label>
          <input className="input" type="email" value={email} onChange={e=>setEmail(e.target.value)} />
        </div>
        <div>
          <label className="muted">Password</label>
          <input className="input" type="password" value={password} onChange={e=>setPassword(e.target.value)} />
        </div>
        <button className="btn btn-primary" disabled={loading}>
          {loading ? 'Signing in…' : 'Login'}
        </button>
      </form>

      <div className="muted" style={{ marginTop:12 }}>
        New here? <Link to="/register">Create an account</Link>
      </div>
    </div>
  )
}
