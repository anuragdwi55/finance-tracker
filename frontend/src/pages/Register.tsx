import React from 'react'
import { useNavigate, Link } from 'react-router-dom'
import api from '../api'

export default function Register(){
  const nav = useNavigate()
  const [email, setEmail] = React.useState('')
  const [password, setPassword] = React.useState('')
  const [confirm, setConfirm] = React.useState('')
  const [loading, setLoading] = React.useState(false)
  const [error, setError] = React.useState('')

  const submit = async (e: React.FormEvent) => {
    e.preventDefault()
    setError('')

    if (!/^[^\s@]+@[^\s@]+\.[^\s@]+$/.test(email)) {
      setError('Enter a valid email'); return
    }
    if (password.length < 8) {
      setError('Password must be at least 8 characters'); return
    }
    if (password !== confirm) {
      setError('Passwords do not match'); return
    }

    setLoading(true)
    try {
      await api.post('/auth/register', { email, password })
      // success → go to login page with a banner
      nav('/login?registered=1')
    } catch (err:any) {
      const status = err?.response?.status
      const msg = err?.response?.data?.message
      if (status === 409) setError(msg || 'Email already registered.')
      else setError(msg || 'Registration failed.')
    } finally {
      setLoading(false)
    }
  }

  return (
    <div className="container" style={{ maxWidth: 420 }}>
      <h2 className="section-title">Create account</h2>

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
          <div className="muted" style={{ fontSize:12, marginTop:6 }}>
            Min 8 chars. Use a mix of letters & numbers.
          </div>
        </div>
        <div>
          <label className="muted">Confirm Password</label>
          <input className="input" type="password" value={confirm} onChange={e=>setConfirm(e.target.value)} />
        </div>
        <button className="btn btn-primary" disabled={loading}>
          {loading ? 'Creating…' : 'Register'}
        </button>
      </form>

      <div className="muted" style={{ marginTop:12 }}>
        Already have an account? <Link to="/login">Login</Link>
      </div>
    </div>
  )
}
