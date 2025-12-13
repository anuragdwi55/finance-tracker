import React, { useEffect, useMemo, useState } from 'react'
import api from '../api'

type Tx = {
  id: number
  category: 'INCOME'|'HOUSING'|'FOOD'|'TRANSPORT'|'UTILITIES'|'ENTERTAINMENT'|'HEALTH'|'INVESTMENT'|'OTHER'
  amount: number
  date: string
  note?: string
}

const categories: Tx['category'][] = ['INCOME','HOUSING','FOOD','TRANSPORT','UTILITIES','ENTERTAINMENT','HEALTH','INVESTMENT','OTHER']
const fmt = (n:number)=> new Intl.NumberFormat().format(n)

export default function Transactions(){
  const [txs, setTxs] = useState<Tx[]>([])
  const [loading, setLoading] = useState(false)
  const [form, setForm] = useState<Partial<Tx>>({category:'FOOD', date: new Date().toISOString().slice(0,10), note:''})
  const [start, setStart] = useState<string>('')
  const [end, setEnd] = useState<string>('')

  const load = async ()=>{
    setLoading(true)
    try { const { data } = await api.get<Tx[]>('/transactions'); setTxs(data) }
    finally { setLoading(false) }
  }

  const add = async (e: React.FormEvent)=>{
    e.preventDefault()
    if(!form.category || !form.amount || !form.date){ alert('category, amount, date required'); return }
    await api.post('/transactions', form)
    await load()
  }

  const remove = async (id:number)=>{
    if(!confirm('Delete transaction?')) return
    await api.delete(`/transactions/${id}`)
    await load()
  }

  const filtered = useMemo(()=>{
    if(!start && !end) return txs
    const s = start? new Date(start).getTime(): -Infinity
    const e = end? new Date(end).getTime(): Infinity
    return txs.filter(t=>{ const d = new Date(t.date+'T00:00:00').getTime(); return d>=s && d<=e })
  },[txs,start,end])

  const totals = useMemo(()=>{
    const income = filtered.filter(t=>t.category==='INCOME').reduce((a,b)=>a+b.amount,0)
    const expense = filtered.filter(t=>t.category!=='INCOME').reduce((a,b)=>a+b.amount,0)
    return { income, expense, savings: income-expense }
  },[filtered])

  useEffect(()=>{ load() },[])

  const box: React.CSSProperties = { padding:16, borderRadius:12, boxShadow:'0 1px 6px rgba(0,0,0,.08)', background:'#fff' }
  const grid: React.CSSProperties = { display:'grid', gridTemplateColumns:'repeat(3,minmax(0,1fr))', gap:16 }
  const tableBox: React.CSSProperties = { overflow:'auto', borderRadius:12, boxShadow:'0 1px 6px rgba(0,0,0,.08)', background:'#fff' }

  return (
    <div style={{ padding:24, maxWidth:960, margin:'0 auto' }}>
      <h2 style={{ fontSize:22, marginBottom:12 }}>Transactions</h2>

      <div style={{ ...grid, marginBottom:24 }}>
        <div style={box}><div style={{fontSize:12,opacity:.7}}>Income</div><div style={{fontSize:20,fontWeight:600}}>₹ {fmt(totals.income)}</div></div>
        <div style={box}><div style={{fontSize:12,opacity:.7}}>Expense</div><div style={{fontSize:20,fontWeight:600}}>₹ {fmt(totals.expense)}</div></div>
        <div style={box}><div style={{fontSize:12,opacity:.7}}>Savings</div><div style={{fontSize:20,fontWeight:600}}>₹ {fmt(totals.savings)}</div></div>
      </div>

      <form onSubmit={add} style={{ ...box, display:'grid', gap:12, gridTemplateColumns:'repeat(5,minmax(0,1fr))', alignItems:'end', marginBottom:16 }}>
        <label style={{display:'grid', gap:6}}>Category
          <select value={form.category} onChange={e=>setForm(f=>({...f, category: e.target.value as Tx['category']}))}>
            {categories.map(c=> <option key={c} value={c}>{c}</option>)}
          </select>
        </label>
        <label style={{display:'grid', gap:6}}>Amount
          <input type="number" step="1" value={form.amount ?? ''} onChange={e=>setForm(f=>({...f, amount: e.target.value === '' ? undefined : Number(e.target.value)}))} placeholder="0"/>
        </label>
        <label style={{display:'grid', gap:6}}>Date
          <input type="date" value={form.date ?? ''} onChange={e=>setForm(f=>({...f, date: e.target.value}))}/>
        </label>
        <label style={{display:'grid', gap:6, gridColumn:'span 2'}}>Note
          <input value={form.note ?? ''} onChange={e=>setForm(f=>({...f, note: e.target.value}))} placeholder="Optional"/>
        </label>
        <button style={{ padding:'8px 12px', borderRadius:8, border:'1px solid #000', background:'#000', color:'#fff' }}>Add</button>
      </form>

      <div style={{display:'flex', gap:12, alignItems:'end', marginBottom:12}}>
        <label style={{display:'grid', gap:6}}>Start
          <input type="date" value={start} onChange={e=>setStart(e.target.value)}/>
        </label>
        <label style={{display:'grid', gap:6}}>End
          <input type="date" value={end} onChange={e=>setEnd(e.target.value)}/>
        </label>
        <button type="button" onClick={()=>{setStart('');setEnd('')}}>Clear</button>
      </div>

      <div style={tableBox}>
        <table style={{ width:'100%', fontSize:14, borderCollapse:'collapse' }}>
          <thead style={{ background:'#f5f5f5' }}>
            <tr>
              <th style={{ textAlign:'left', padding:12 }}>Date</th>
              <th style={{ textAlign:'left', padding:12 }}>Category</th>
              <th style={{ textAlign:'right', padding:12 }}>Amount</th>
              <th style={{ textAlign:'left', padding:12 }}>Note</th>
              <th style={{ padding:12 }}>Actions</th>
            </tr>
          </thead>
          <tbody>
            {loading && <tr><td style={{padding:12}} colSpan={5}>Loading…</td></tr>}
            {!loading && filtered.map(t=> (
              <tr key={t.id} style={{ borderTop:'1px solid #eee' }}>
                <td style={{ padding:12, whiteSpace:'nowrap' }}>{t.date}</td>
                <td style={{ padding:12 }}>{t.category}</td>
                <td style={{ padding:12, textAlign:'right' }}>₹ {fmt(t.amount)}</td>
                <td style={{ padding:12 }}>{t.note}</td>
                <td style={{ padding:12, textAlign:'center' }}>
                  <button onClick={()=>remove(t.id)}>Delete</button>
                </td>
              </tr>
            ))}
            {!loading && filtered.length===0 && <tr><td style={{padding:12}} colSpan={5}>No transactions.</td></tr>}
          </tbody>
        </table>
      </div>
    </div>
  )
}
