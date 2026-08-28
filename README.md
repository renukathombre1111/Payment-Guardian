# Payment Guardian

An AI safety layer that evaluates proposed financial actions **before money moves**.

NovaTech Pvt Ltd is a **synthetic company**. This MVP never talks to a real bank. All transactions, vendors, and evaluation metrics are clearly synthetic.

## Why it is required??
AI agents can increasingly initiate financial actions, but financial decisions require stronger controls than ordinary software actions.
Payment Guardian adds a defense layer that verifies evidence, applies deterministic policies and risk signals, simulates financial impact, and keeps a human responsible for the final decision.

**AI recommends — humans decide. No autonomous money movement.**

```
User / AI Agent
      │  "Pay ₹18,50,000 to Vendor ABC"
      ▼
Payment Guardian
  1. Gather evidence (payment, vendor, invoice, bank, history, cash)
  2. Policy engine (company rules)
  3. Risk engine (12 deterministic signals → score 0–100)
  4. Simulate cash scenarios
  5. AI investigator explains (structured; LLM optional)
      │
 APPROVE / REVIEW / BLOCK
      │
 Human: APPROVE · HOLD · REJECT · ESCALATE (audit trail)
      │
 Simulated Razorpay stub (no real transfer)
```

## Stack

| Layer | Technology |
| --- | --- |
| Frontend | React + Vite + Tailwind + Axios + React Router + Recharts |
| Backend | Java 17 + Spring Boot 3.3 |
| Database | H2 in-memory (default) or PostgreSQL |
| Data / eval | Python scripts in `data/` |

## Run locally

### Backend

Requires JDK 17+ and Maven.

```powershell
cd backend
mvn spring-boot:run
```

Health: http://localhost:8080/api/health  
H2 console: http://localhost:8080/h2-console (JDBC `jdbc:h2:mem:paymentguardian`)

### Frontend

Requires Node.js 18+.

```powershell
cd frontend
npm install
npm run dev
```

Open http://localhost:5173

### PostgreSQL (optional)

```powershell
docker compose up -d
cd backend
mvn spring-boot:run -Dspring-boot.run.profiles=postgres
```

## Demo scenarios (seeded on startup)

| Case | Vendor | Amount | Expected |
| --- | --- | --- | --- |
| REVIEW | ABC Technologies | ₹18,50,000 | Invoice matches; bank changed ~19h ago; ~4.6× history |
| APPROVE | OfficeKart Supplies | ₹24,000 | Normal small payment, stable vendor |
| BLOCK | Shadow IT Consulting | ₹25,00,000 | New vendor, new bank, no matching invoice for amount, high risk |

**Pay-after-receivable:** use simulation panel — paying after the ₹35L Apex Retail receivable preserves stronger minimum cash than paying today.

## Architecture

### Risk engine (12 deterministic signals)

Each signal: name, severity, evidence, numeric contribution, explanation.

| Signal | Description |
| --- | --- |
| amount | vs historical average |
| vendor | baseline vendor risk / payment history |
| bank-account-change | cooling period, new account |
| invoice | matching open invoice |
| duplicate-invoice | duplicate number or paid amount |
| payment-frequency | vs typical cadence |
| unusual-timing | weekend / month-end |
| vendor-age | days since onboarding |
| cash-flow-pressure | vs min cash buffer |
| historical-anomaly | statistical outlier |
| policy-violation | company rule breaches |
| split-payment | split to circumvent limits |

**Score → decision**

| Score | Band | Action |
| --- | --- | --- |
| 0–30 | LOW | APPROVE |
| 31–60 | MEDIUM | REVIEW |
| 61–80 | HIGH | REVIEW |
| 81–100 | CRITICAL | BLOCK |

Critical policy violations (e.g. missing required invoice) force BLOCK regardless of score.

### Policy engine (configurable via `application.yml`)

| Rule | Default |
| --- | --- |
| Max payment without approval | ₹5,00,000 |
| Bank account cooling period | 24 hours |
| New vendor max payment | ₹2,00,000 |
| Min cash buffer | ₹25,00,000 |
| Invoice required | yes |

### Evidence chain

Payment → Vendor → Invoice → Bank Account → Historical Txns → Cash Position → Policy Violations → Risk Signals → Final Decision

### Human approval & audit

Actions: `APPROVE`, `HOLD`, `REJECT`, `ESCALATE` — all logged to audit trail. Approve enables **simulated** Razorpay payout only; no real money moves.

## API

| Method | Path | Description |
| --- | --- | --- |
| GET | `/api/health` | Service status |
| GET | `/api/policy` | Active company policy |
| GET | `/api/dashboard` | Dashboard stats |
| GET | `/api/cases` | All payment cases |
| POST | `/api/payments/evaluate` | Evaluate a payment |
| GET | `/api/cases/{id}` | Case detail + signals |
| GET | `/api/cases/{id}/evidence` | Evidence chain |
| GET | `/api/cases/{id}/audit` | Audit trail |
| POST | `/api/simulate-payment` | Cash scenarios |
| POST | `/api/cases/{id}/chat` | AI investigator Q&A |
| POST | `/api/cases/{id}/approve` | Human approve |
| POST | `/api/cases/{id}/hold` | Human hold |
| POST | `/api/cases/{id}/reject` | Human reject |
| POST | `/api/cases/{id}/escalate` | Escalate |
| POST | `/api/cases/{id}/razorpay/payout` | Simulated payout (after approve) |
| GET | `/api/vendors/{id}/profile` | Vendor profile |
| GET | `/api/evaluation/metrics` | Offline eval metrics |

## Synthetic dataset & evaluation

```powershell
cd data
python generate_transactions.py   # 5000 cases + ground truth
python evaluate.py                # train/val/test metrics → out/evaluation_metrics.json
```

Metrics (precision, recall, F1, FPR, FNR, confusion matrix) appear on the **Evaluation** page after running the pipeline. The backend serves metrics from `data/out/evaluation_metrics.json` — it does not fabricate numbers.

## Tests

```powershell
cd backend
mvn test
```

## Limitations

- Real bank production integration
- Autonomous payment execution
- Live LLM (set `OPENAI_API_KEY` for future Spring AI wiring; chat uses structured fallback today)
- JWT auth / pgvector

## License

Synthetic demo software — not for production financial use.
