"""Offline evaluation pipeline: train/val/test split with precision, recall, F1, FPR, FNR, confusion matrix.

Uses a deterministic rules engine mirroring Payment Guardian's Java risk/policy logic.
Run after generate_transactions.py. Writes data/out/evaluation_metrics.json for the dashboard.
"""
from __future__ import annotations

import csv
import json
import random
from collections import defaultdict
from pathlib import Path

DECISIONS = ["APPROVE", "REVIEW", "BLOCK"]


def predict(row: dict) -> str:
    """Mirror simplified Java RiskEngine + PolicyEngine for offline benchmark."""
    score = 0

    amount = int(row["amount"])
    multiplier = float(row["amount_multiplier"])
    if multiplier > 4:
        score += 20
    elif multiplier > 2:
        score += 12
    elif multiplier > 1.5:
        score += 6

    vendor_risk = int(row["vendor_risk_score"])
    if vendor_risk >= 70:
        score += 15
    elif vendor_risk >= 40:
        score += 8

    bank_h = int(row["bank_changed_hours"])
    if bank_h < 24:
        score += 18 if bank_h < 12 else 12

    if row["has_invoice"] != "True" and row["has_invoice"] is not True:
        score += 25

    if row["duplicate_invoice"] == "True" or row["duplicate_invoice"] is True:
        score += 15

    vendor_age = int(row["vendor_age_days"])
    if vendor_age < 30:
        score += 12
    elif vendor_age < 90:
        score += 6

    if row["cash_pressure"] == "True" or row["cash_pressure"] is True:
        score += 10

    if amount > 500_000:
        score += 8
    if vendor_age < 90 and amount > 200_000:
        score += 8

    if row["weekend"] == "True" or row["weekend"] is True:
        score += 5

    score = min(100, score)

    if row["has_invoice"] != "True" and row["has_invoice"] is not True:
        return "BLOCK"
    if score >= 81:
        return "BLOCK"
    if score >= 31:
        return "REVIEW"
    return "APPROVE"


def _parse_bool(val) -> bool:
    if isinstance(val, bool):
        return val
    return str(val).lower() == "true"


def load_rows(path: Path) -> list[dict]:
    rows = list(csv.DictReader(path.open(encoding="utf-8")))
    for r in rows:
        r["has_invoice"] = _parse_bool(r["has_invoice"])
        r["duplicate_invoice"] = _parse_bool(r["duplicate_invoice"])
        r["cash_pressure"] = _parse_bool(r["cash_pressure"])
        r["weekend"] = _parse_bool(r["weekend"])
    return rows


def split(rows: list[dict], seed: int = 42) -> tuple[list, list, list]:
    rng = random.Random(seed)
    shuffled = rows[:]
    rng.shuffle(shuffled)
    n = len(shuffled)
    train_end = int(n * 0.7)
    val_end = int(n * 0.85)
    return shuffled[:train_end], shuffled[train_end:val_end], shuffled[val_end:]


def confusion(actual: list[str], predicted: list[str]) -> dict[str, dict[str, int]]:
    matrix: dict[str, dict[str, int]] = {a: {p: 0 for p in DECISIONS} for a in DECISIONS}
    for a, p in zip(actual, predicted):
        matrix[a][p] += 1
    return matrix


def metrics(actual: list[str], predicted: list[str]) -> dict:
    matrix = confusion(actual, predicted)
    tp_review = matrix["REVIEW"]["REVIEW"] + matrix["BLOCK"]["BLOCK"]
    fp_review = matrix["APPROVE"]["REVIEW"] + matrix["APPROVE"]["BLOCK"]
    fn_review = matrix["REVIEW"]["APPROVE"] + matrix["BLOCK"]["APPROVE"]
    tn_review = matrix["APPROVE"]["APPROVE"]

    precision = tp_review / max(tp_review + fp_review, 1)
    recall = tp_review / max(tp_review + fn_review, 1)
    f1 = 2 * precision * recall / max(precision + recall, 1e-9)

    fp_total = sum(matrix["APPROVE"][p] for p in ("REVIEW", "BLOCK"))
    fn_total = sum(matrix[a]["APPROVE"] for a in ("REVIEW", "BLOCK"))
    safe_total = matrix["APPROVE"]["APPROVE"] + fp_total
    danger_total = tp_review + fn_total

    return {
        "precision": round(precision, 4),
        "recall": round(recall, 4),
        "f1": round(f1, 4),
        "falsePositiveRate": round(fp_total / max(safe_total, 1), 4),
        "falseNegativeRate": round(fn_total / max(danger_total, 1), 4),
        "confusionMatrix": matrix,
    }


def main() -> None:
    root = Path(__file__).parent / "out"
    csv_path = root / "transactions.csv"
    if not csv_path.exists():
        print("Run generate_transactions.py first.")
        return

    rows = load_rows(csv_path)
    train, val, test = split(rows)

    for name, subset in [("train", train), ("val", val), ("test", test)]:
        actual = [r["ground_truth"] for r in subset]
        predicted = [predict(r) for r in subset]
        m = metrics(actual, predicted)
        print(f"=== {name} ({len(subset)} cases) ===")
        print(f"  Precision: {m['precision']:.1%}")
        print(f"  Recall:    {m['recall']:.1%}")
        print(f"  F1:        {m['f1']:.1%}")
        print(f"  FPR:       {m['falsePositiveRate']:.1%}")
        print(f"  FNR:       {m['falseNegativeRate']:.1%}")

    test_actual = [r["ground_truth"] for r in test]
    test_pred = [predict(r) for r in test]
    test_m = metrics(test_actual, test_pred)

    payload = {
        "totalCases": len(rows),
        "trainSize": len(train),
        "valSize": len(val),
        "testSize": len(test),
        "precision": test_m["precision"],
        "recall": test_m["recall"],
        "f1": test_m["f1"],
        "falsePositiveRate": test_m["falsePositiveRate"],
        "falseNegativeRate": test_m["falseNegativeRate"],
        "confusionMatrix": test_m["confusionMatrix"],
        "datasetNote": (
            f"Synthetic NovaTech dataset ({len(rows)} cases). "
            "Held-out test split (15%). Rules mirror Java RiskEngine/PolicyEngine — not live API inference."
        ),
    }
    out_path = root / "evaluation_metrics.json"
    out_path.write_text(json.dumps(payload, indent=2), encoding="utf-8")
    print(f"\nWrote metrics to {out_path}")


if __name__ == "__main__":
    main()
