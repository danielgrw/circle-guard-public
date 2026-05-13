#!/usr/bin/env python3
"""Parse Locust CSV stats and print one WARNING line for Jenkins logs (advisory)."""

from __future__ import annotations

import csv
import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
STATS = ROOT / "locust_results_stats.csv"


def main() -> int:
    if not STATS.exists():
        print("WARNING: no locust_results_stats.csv — Locust may not have run or wrote elsewhere")
        return 0

    with STATS.open(encoding="utf-8") as f:
        reader = csv.DictReader(f)
        rows = list(reader)

    agg = None
    for row in rows:
        name = (row.get("Name") or "").strip()
        typ = (row.get("Type") or "").strip()
        if name == "Aggregated" or typ == "Aggregated":
            agg = row
            break

    if agg is None and rows:
        agg = rows[-1]

    if not agg:
        print("WARNING: Locust stats CSV empty")
        return 0

    p95 = agg.get("95%") or agg.get("95") or "?"
    rps = agg.get("Requests/s") or agg.get("Total Request Count") or "?"
    fails = agg.get("Failure Count") or "?"
    total = agg.get("Request Count") or "?"

    print(
        f"WARNING: Locust aggregate — request_count={total} failures={fails} "
        f"p95_ms={p95} req_per_s={rps} (advisory; non-blocking)"
    )
    return 0


if __name__ == "__main__":
    sys.exit(main())
