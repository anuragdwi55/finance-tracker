// src/App.tsx
import './styles.css'
import React from 'react'
import {
  BrowserRouter, Routes, Route, NavLink, Navigate, useLocation
} from 'react-router-dom'

import Login from './pages/Login'
import Register from './pages/Register'
import Dashboard from './pages/Dashboard'
import Transactions from './pages/Transactions'
import Budgets from './pages/Budgets'
import Alerts from './pages/Alerts'
import Goals from './pages/Goals'
import Bills from './pages/Bills'
import ImportCSV from './pages/Import'
import ReportPreview from './pages/ReportPreview'
import api from './api'

const RequireAuth: React.FC<{ children: React.ReactNode }> = ({ children }) => {
  const [ok, setOk] = React.useState<boolean | null>(null)
  React.useEffect(() => {
    const token = localStorage.getItem('token')
    if (!token) { setOk(false); return }
    (async () => {
      try { await api.get('/auth/me'); setOk(true) }
      catch { setOk(false) }
    })()
  }, [])
  if (ok === null) return <div className="container">Checking session…</div>
  return ok ? <>{children}</> : <Navigate to="/login" replace />
}

/** Full app navbar (unchanged) */
function Nav() {
  const [unread, setUnread] = React.useState(0)
  const [email, setEmail] = React.useState<string | null>(null)
  const token = localStorage.getItem('token')

  React.useEffect(() => {
    if (!token) { setEmail(null); setUnread(0); return }
    let cancelled = false
    const fetchMe = async () => {
      try {
        const { data } = await api.get('/auth/me')
        if (!cancelled) setEmail(data?.email ?? null)
      } catch { if (!cancelled) setEmail(null) }
    }
    const fetchCount = async () => {
      try {
        const { data } = await api.get('/alerts/unread-count')
        if (!cancelled) setUnread(data?.count ?? 0)
      } catch {}
    }
    fetchMe()
    fetchCount()
    const id = setInterval(fetchCount, 10000)
    return () => { cancelled = true; clearInterval(id) }
  }, [token])

  const link = ({ isActive }: { isActive: boolean }) => `nav-link${isActive ? ' active' : ''}`
  const logout = () => { localStorage.removeItem('token'); window.location.href = '/login' }

  return (
    <nav className="navbar">
      <div className="brand">FinTrack</div>
      <div className="spacer" />
      <NavLink to="/" className={link}>Dashboard</NavLink>
      <NavLink to="/transactions" className={link}>Transactions</NavLink>
      <NavLink to="/budgets" className={link}>Budgets</NavLink>
      <NavLink to="/alerts" className={link}>
        Alerts {unread ? <span className="badge" style={{ marginLeft: 6 }}>{unread}</span> : null}
      </NavLink>
      <NavLink to="/goals" className={link}>Goals</NavLink>
      <NavLink to="/bills" className={link}>Bills</NavLink>
      <NavLink to="/import" className={link}>Import</NavLink>
      <div className="spacer" />
      {email && <span className="muted" style={{ marginRight: 8 }}>{email}</span>}
      {!token && <NavLink to="/login" className={link}>Login</NavLink>}
      {token
        ? <button className="btn" onClick={logout}>Logout</button>
        : <NavLink to="/register" className={link}>Register</NavLink>}
    </nav>
  )
}

/** NEW: A tiny header that shows only the brand logo/text */
function LogoBar() {
  return (
    <header className="authbar">
      <div className="brand">FinTrack</div>
    </header>
  )
}

/** Shell that picks which header to render */
function AppShell() {
  const { pathname } = useLocation()
  const onAuth = pathname.startsWith('/login') || pathname.startsWith('/register')
  return (
    <>
      {onAuth ? <LogoBar /> : <Nav />}
      <Routes>
        {/* Auth (logo-only header) */}
        <Route path="/login" element={<Login />} />
        <Route path="/register" element={<Register />} />

        {/* Protected (full navbar) */}
        <Route path="/" element={<RequireAuth><Dashboard /></RequireAuth>} />
        <Route path="/transactions" element={<RequireAuth><Transactions /></RequireAuth>} />
        <Route path="/budgets" element={<RequireAuth><Budgets /></RequireAuth>} />
        <Route path="/alerts" element={<RequireAuth><Alerts /></RequireAuth>} />
        <Route path="/goals" element={<RequireAuth><Goals /></RequireAuth>} />
        <Route path="/bills" element={<RequireAuth><Bills /></RequireAuth>} />
        <Route path="/import" element={<RequireAuth><ImportCSV /></RequireAuth>} />
        <Route path="/report-preview" element={<RequireAuth><ReportPreview /></RequireAuth>} />
        <Route path="*" element={<Navigate to="/" replace />} />
      </Routes>
    </>
  )
}

export default function App() {
  return (
    <BrowserRouter>
      <AppShell />
    </BrowserRouter>
  )
}
