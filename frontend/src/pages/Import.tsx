import React, { useState } from 'react'
import api from '../api'

type Row = { date?: string; amount?: number; description?: string; category?: string; error?: string }
type Mapping = { date?: string; amount?: string; description?: string; category?: string; debit?: string; credit?: string; dateFormat?: string; amountIsCreditMinusDebit?: boolean }
type Preview = { uploadId: string; totalRows: number; sample: Row[]; detected: Mapping }

export default function ImportCSV(){
  const [file, setFile] = useState<File|null>(null)
  const [mapping, setMapping] = useState<Mapping>({})
  const [preview, setPreview] = useState<Preview|null>(null)
  const [selected, setSelected] = useState<Set<number>>(new Set())

  const onFile = (e: React.ChangeEvent<HTMLInputElement>)=>{
    setFile(e.target.files?.[0] || null)
    setPreview(null)
    setSelected(new Set())
  }

  const doPreview = async ()=>{
    if (!file) return alert('Choose a CSV first')
    const fd = new FormData()
    fd.append('file', file)
    fd.append('mapping', new Blob([JSON.stringify(mapping)], { type: 'application/json' }))
    const { data } = await api.post<Preview>('/import/preview', fd, { headers: { 'Content-Type': 'multipart/form-data' } })
    setPreview(data)
    setSelected(new Set(Array.from({length: data.sample.length}, (_,i)=>i))) // preselect visible
    // If server detected mapping, show it as placeholders
    if (data.detected) setMapping(m=>({ ...data.detected, ...m }))
  }

  const commit = async ()=>{
    if (!preview) return
    // in preview we only saw first 50 but server staged ALL rows; sending 'selected' empty means import all
    const sel = Array.from(selected.values())
    const { data } = await api.post('/import/commit', { uploadId: preview.uploadId, selected: undefined }) // import all
    alert(`Imported ${data.imported}, Duplicates ${data.skippedDuplicates}, Failed ${data.failed}`)
    setPreview(null); setFile(null); setSelected(new Set()); (document.getElementById('csvfile') as HTMLInputElement).value = ''
  }

  const card: React.CSSProperties = { padding:16, borderRadius:12, boxShadow:'0 1px 6px rgba(0,0,0,.08)', background:'#fff' }
  const inputStyle: React.CSSProperties = { width:200, padding: '6px 10px' }

  return (
    <div style={{ padding:24, maxWidth:1000, margin:'0 auto' }}>
      <h2 style={{ fontSize:22, marginBottom:12 }}>Import Transactions (CSV)</h2>

      <div style={{ display:'grid', gridTemplateColumns:'2fr 1fr', gap:16 }}>
        <div style={card}>
          <div style={{ display:'flex', gap:8, alignItems:'center', flexWrap:'wrap' }}>
            <input id="csvfile" type="file" accept=".csv,text/csv" onChange={onFile} />
            <input placeholder="date column (e.g. date)" value={mapping.date||''} onChange={e=>setMapping(m=>({...m, date:e.target.value}))} style={inputStyle}/>
            <input placeholder="amount column" value={mapping.amount||''} onChange={e=>setMapping(m=>({...m, amount:e.target.value}))} style={inputStyle}/>
            <input placeholder="description column" value={mapping.description||''} onChange={e=>setMapping(m=>({...m, description:e.target.value}))} style={inputStyle}/>
            <input placeholder="category column (optional)" value={mapping.category||''} onChange={e=>setMapping(m=>({...m, category:e.target.value}))} style={inputStyle}/>
            <input placeholder="debit column (if no amount)" value={mapping.debit||''} onChange={e=>setMapping(m=>({...m, debit:e.target.value}))} style={inputStyle}/>
            <input placeholder="credit column (if no amount)" value={mapping.credit||''} onChange={e=>setMapping(m=>({...m, credit:e.target.value}))} style={inputStyle}/>
            <input placeholder="date format (optional, e.g. dd/MM/uuuu)" value={mapping.dateFormat||''} onChange={e=>setMapping(m=>({...m, dateFormat:e.target.value}))} style={{ width:220 }}/>
            <label><input type="checkbox" checked={!!mapping.amountIsCreditMinusDebit} onChange={e=>setMapping(m=>({...m, amountIsCreditMinusDebit:e.target.checked}))}/> amount = credit - debit</label>
            <button onClick={doPreview}>Preview</button>
          </div>
        </div>

        <div style={card}>
          <div style={{ fontWeight:600, marginBottom:8 }}>Tips</div>
          <ul style={{ fontSize:13, marginTop:0 }}>
            <li>Headers must be on the first row.</li>
            <li>If your bank exports Debit/Credit separately, set <i>debit</i>/<i>credit</i> and tick “credit - debit”.</li>
            <li>Supported dates: yyyy-MM-dd, dd/MM/yyyy, MM/dd/yyyy, dd-MMM-uuuu.</li>
          </ul>
        </div>
      </div>

      {preview && (
        <div style={{ ...card, marginTop:16 }}>
          <div style={{ marginBottom:8 }}>Total rows detected: <b>{preview.totalRows}</b> (showing first 50)</div>
          <div style={{ overflow:'auto' }}>
            <table style={{ width:'100%', fontSize:13, borderCollapse:'collapse' }}>
              <thead><tr><th align="left">#</th><th align="left">Date</th><th align="right">Amount</th><th align="left" style={{ padding:'6px 8px' }}>Description</th><th align="left">Category</th><th align="left">Error</th></tr></thead>
              <tbody>
                {preview.sample.map((r, i)=>(
                  <tr key={i} style={{ borderTop:'1px solid #eee' }}>
                    <td>{i+1}</td>
                    <td>{r.date || ''}</td>
                    <td align="right">{r.amount ?? ''}</td>
                    <td>{r.description || ''}</td>
                    <td>{r.category || ''}</td>
                    <td style={{ color:'#900' }}>{r.error || ''}</td>
                  </tr>
                ))}
              </tbody>
            </table>
          </div>
          <div style={{ marginTop:12 }}>
            <button onClick={commit}>Import All</button>
          </div>
        </div>
      )}
    </div>
  )
}
