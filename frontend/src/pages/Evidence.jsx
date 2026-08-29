import { useEffect, useState } from 'react'
import { useParams } from 'react-router-dom'
import { api, inr } from '../api.js'

const tone = {
  CRITICAL: 'bg-rose-500',
  HIGH: 'bg-rose-500',
  MEDIUM: 'bg-amber-400',
  LOW: 'bg-emerald-400',
  INFO: 'bg-slate-500',
}

export default function Evidence() {
  const { id } = useParams()
  const [detail, setDetail] = useState(null)
  const [audit, setAudit] = useState([])
  const [caseId, setCaseId] = useState(id)

  useEffect(() => {
    async function load() {
      let target = id
      if (!target) {
        const cases = await api.get('/cases')
        target = cases.data[0]?.id
        setCaseId(target)
      }
      if (!target) return
      const d = await api.get(`/cases/${target}/evidence`)
      setDetail(d.data)
      try {
        const a = await api.get(`/cases/${target}/audit`)
        setAudit(a.data)
      } catch {
        setAudit([])
      }
    }
    load()
  }, [id])

  if (!detail) return <p className="p-6 text-slate-400">Loading evidence…</p>

  return (
    <main className="p-6 max-w-4xl">
      <h2 className="text-xl font-semibold">Evidence chain</h2>
      <p className="text-slate-400 mt-1">
        Case #{caseId} · {detail.context.vendor} · {inr(detail.context.amount)} · {detail.decision}
      </p>

      <div className="mt-6 relative border-l border-line ml-3 space-y-6">
        {detail.evidence.map((node) => (
          <div key={node.id} className="ml-6 relative">
            <div className={`absolute -left-[1.85rem] top-1 h-4 w-4 rounded-full ${tone[node.severity] || 'bg-slate-500'}`} />
            <p className="text-sm uppercase tracking-widest text-slate-500">{node.label}</p>
            <p className="text-slate-100">{node.detail}</p>
          </div>
        ))}
      </div>

      {detail.vendorProfile && (
        <div className="mt-8 rounded-2xl border border-line bg-panel p-5">
          <h3 className="font-medium">Vendor profile</h3>
          <dl className="mt-3 grid grid-cols-2 gap-2 text-sm">
            <dt className="text-slate-500">Age</dt><dd>{detail.vendorProfile.vendorAgeDays} days</dd>
            <dt className="text-slate-500">Payments</dt><dd>{detail.vendorProfile.paymentCount}</dd>
            <dt className="text-slate-500">Average</dt><dd>{inr(detail.vendorProfile.averagePayment)}</dd>
            <dt className="text-slate-500">Account</dt><dd>{detail.vendorProfile.activeAccount}</dd>
            <dt className="text-slate-500">Account age</dt><dd>{detail.vendorProfile.accountAgeHours}h</dd>
            <dt className="text-slate-500">Duplicate risk</dt><dd>{detail.vendorProfile.duplicateInvoiceRisk ? 'Yes' : 'No'}</dd>
          </dl>
        </div>
      )}

      <div className="mt-8 rounded-2xl border border-line bg-panel p-5">
        <p className="text-amber-300 font-medium">Final decision: {detail.decision} ({detail.riskBand}, {detail.riskScore}/100)</p>
        <p className="mt-2 text-slate-300">{detail.recommendation}</p>
        <p className="mt-3 text-sm text-slate-500">
          Deterministic risk engine — AI investigator explains; humans approve. No money moved.
        </p>
      </div>

      {audit.length > 0 && (
        <div className="mt-8 rounded-2xl border border-line bg-panel p-5">
          <h3 className="font-medium">Audit trail</h3>
          <ul className="mt-3 space-y-2 text-sm">
            {audit.map((e) => (
              <li key={e.id} className="border-b border-line/50 pb-2">
                <span className="text-emerald-400">{e.action}</span> by {e.actor}
                {e.note && <span className="text-slate-400"> — {e.note}</span>}
              </li>
            ))}
          </ul>
        </div>
      )}
    </main>
  )
}
