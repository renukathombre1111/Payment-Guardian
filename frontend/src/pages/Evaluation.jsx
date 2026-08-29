import { useEffect, useState } from 'react'
import { api } from '../api.js'
import { Bar, BarChart, Cell, ResponsiveContainer, Tooltip, XAxis, YAxis } from 'recharts'

export default function Evaluation() {
  const [metrics, setMetrics] = useState(null)
  const [error, setError] = useState('')

  useEffect(() => {
    api.get('/evaluation/metrics')
      .then((r) => setMetrics(r.data))
      .catch((e) => setError(e.message))
  }, [])

  if (error) return <p className="p-6 text-rose-400">{error}</p>
  if (!metrics) return <p className="p-6 text-slate-400">Loading evaluation metrics…</p>

  const hasData = metrics.totalCases > 0
  const cm = metrics.confusionMatrix || {}
  const chartData = ['APPROVE', 'REVIEW', 'BLOCK'].map((label) => ({
    label,
    count: Object.values(cm[label] || {}).reduce((a, b) => a + b, 0),
  }))

  return (
    <main className="p-6 max-w-4xl">
      <h2 className="text-xl font-semibold">Evaluation dashboard</h2>
      <p className="text-slate-400 mt-1">{metrics.datasetNote}</p>

      {!hasData ? (
        <div className="mt-6 rounded-2xl border border-amber-500/40 bg-amber-500/10 p-5 text-amber-200">
          <p>No evaluation metrics yet. Generate synthetic data first:</p>
          <pre className="mt-3 text-sm bg-ink/50 p-3 rounded-lg overflow-x-auto">{`cd data
python generate_transactions.py
python evaluate.py`}</pre>
        </div>
      ) : (
        <>
          <section className="mt-6 grid grid-cols-2 lg:grid-cols-4 gap-4">
            <Metric label="Total cases" value={metrics.totalCases} />
            <Metric label="Train / Val / Test" value={`${metrics.trainSize} / ${metrics.valSize} / ${metrics.testSize}`} />
            <Metric label="Precision (test)" value={`${(metrics.precision * 100).toFixed(1)}%`} />
            <Metric label="Recall (test)" value={`${(metrics.recall * 100).toFixed(1)}%`} />
            <Metric label="F1 (test)" value={metrics.f1.toFixed(3)} />
            <Metric label="FPR (test)" value={`${(metrics.falsePositiveRate * 100).toFixed(1)}%`} />
            <Metric label="FNR (test)" value={`${(metrics.falseNegativeRate * 100).toFixed(1)}%`} />
          </section>

          <section className="mt-8 rounded-2xl border border-line bg-panel p-6">
            <h3 className="font-medium">Confusion matrix (held-out test)</h3>
            <table className="mt-4 w-full text-sm">
              <thead>
                <tr className="text-slate-400">
                  <th className="text-left py-2">Actual ↓ / Predicted →</th>
                  <th>APPROVE</th>
                  <th>REVIEW</th>
                  <th>BLOCK</th>
                </tr>
              </thead>
              <tbody>
                {['APPROVE', 'REVIEW', 'BLOCK'].map((actual) => (
                  <tr key={actual} className="border-t border-line/50">
                    <td className="py-2 text-slate-300">{actual}</td>
                    {['APPROVE', 'REVIEW', 'BLOCK'].map((pred) => (
                      <td key={pred} className="text-center">{cm[actual]?.[pred] ?? 0}</td>
                    ))}
                  </tr>
                ))}
              </tbody>
            </table>
          </section>

          <section className="mt-8 rounded-2xl border border-line bg-panel p-6">
            <h3 className="font-medium">Predictions by actual label</h3>
            <div className="h-48 mt-4">
              <ResponsiveContainer width="100%" height="100%">
                <BarChart data={chartData}>
                  <XAxis dataKey="label" stroke="#94a3b8" />
                  <YAxis stroke="#94a3b8" />
                  <Tooltip />
                  <Bar dataKey="count">
                    {chartData.map((_, i) => (
                      <Cell key={i} fill={['#34d399', '#fbbf24', '#f87171'][i]} />
                    ))}
                  </Bar>
                </BarChart>
              </ResponsiveContainer>
            </div>
          </section>
        </>
      )}
    </main>
  )
}

function Metric({ label, value }) {
  return (
    <div className="rounded-2xl border border-line bg-panel p-4">
      <p className="text-xs uppercase tracking-widest text-slate-400">{label}</p>
      <p className="mt-2 text-xl font-semibold">{value}</p>
    </div>
  )
}
