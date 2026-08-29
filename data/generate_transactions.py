"""Generate ≥5000 synthetic payment cases with ground-truth labels for offline evaluation."""
from __future__ import annotations

import csv
import json
import random
from pathlib import Path

SCENARIOS = [
    "normal_payment",
    "large_payment",
    "new_bank_account",
    "duplicate_invoice",
    "missing_invoice",
    "late_receivable",
    "cash_shortage",
    "vendor_anomaly",
    "sudden_payment_spike",
    "unusual_timing",
    "new_vendor",
    "policy_breach",
]

# Ground-truth expected decisions (human label for evaluation)
SCENARIO_LABEL = {
    "normal_payment": "APPROVE",
    "large_payment": "REVIEW",
    "new_bank_account": "REVIEW",
    "duplicate_invoice": "REVIEW",
    "missing_invoice": "BLOCK",
    "late_receivable": "REVIEW",
    "cash_shortage": "REVIEW",
    "vendor_anomaly": "REVIEW",
    "sudden_payment_spike": "REVIEW",
    "unusual_timing": "REVIEW",
    "new_vendor": "REVIEW",
    "policy_breach": "BLOCK",
}


def generate(n: int = 5000, seed: int = 42) -> list[dict]:
    rng = random.Random(seed)
    rows: list[dict] = []
    per_scenario = max(1, n // len(SCENARIOS))

    idx = 0
    for scenario in SCENARIOS:
        for _ in range(per_scenario):
            idx += 1
            amount = _amount_for(rng, scenario)
            rows.append(_row(idx, rng, scenario, amount))

    while len(rows) < n:
        idx += 1
        scenario = rng.choice(SCENARIOS)
        rows.append(_row(idx, rng, scenario, _amount_for(rng, scenario)))

    rng.shuffle(rows)
    return rows[:n]


def _amount_for(rng: random.Random, scenario: str) -> int:
    if scenario in {"large_payment", "sudden_payment_spike", "policy_breach"}:
        return rng.randint(1_500_000, 3_000_000)
    if scenario == "normal_payment":
        return rng.randint(50_000, 450_000)
    if scenario == "new_vendor":
        return rng.randint(250_000, 800_000)
    return rng.randint(100_000, 1_200_000)


def _row(idx: int, rng: random.Random, scenario: str, amount: int) -> dict:
    vendor_age_days = rng.randint(10, 800)
    if scenario == "new_vendor":
        vendor_age_days = rng.randint(5, 60)
    bank_changed_hours = rng.randint(0, 72)
    if scenario == "new_bank_account":
        bank_changed_hours = rng.randint(1, 20)
    has_invoice = scenario not in {"missing_invoice"}
    duplicate_invoice = scenario == "duplicate_invoice"
    cash_pressure = scenario in {"cash_shortage", "policy_breach"}
    multiplier = round(rng.uniform(0.8, 5.5) if scenario == "sudden_payment_spike" else rng.uniform(0.9, 2.5), 2)
    if scenario == "normal_payment":
        multiplier = round(rng.uniform(0.9, 1.3), 2)

    return {
        "id": idx,
        "vendor_id": rng.randint(1, 200),
        "amount": amount,
        "scenario": scenario,
        "ground_truth": SCENARIO_LABEL[scenario],
        "vendor_age_days": vendor_age_days,
        "bank_changed_hours": bank_changed_hours,
        "has_invoice": has_invoice,
        "duplicate_invoice": duplicate_invoice,
        "cash_pressure": cash_pressure,
        "amount_multiplier": multiplier,
        "weekend": scenario == "unusual_timing",
        "vendor_risk_score": rng.randint(60, 90) if scenario == "vendor_anomaly" else rng.randint(5, 40),
    }


def main() -> None:
    out = Path(__file__).parent / "out"
    out.mkdir(exist_ok=True)
    rows = generate(5000)
    fieldnames = list(rows[0].keys())
    with (out / "transactions.csv").open("w", newline="", encoding="utf-8") as f:
        writer = csv.DictWriter(f, fieldnames=fieldnames)
        writer.writeheader()
        writer.writerows(rows)

    labels = {str(r["id"]): r["ground_truth"] for r in rows}
    (out / "ground_truth.json").write_text(json.dumps(labels, indent=2), encoding="utf-8")
    print(f"Wrote {len(rows)} synthetic cases to {out / 'transactions.csv'}")


if __name__ == "__main__":
    main()
