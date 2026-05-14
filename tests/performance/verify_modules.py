#!/usr/bin/env python3
"""Smoke check: three HttpUser scenario classes load (no live services required)."""

from __future__ import annotations

import sys
from pathlib import Path

ROOT = Path(__file__).resolve().parent
if str(ROOT) not in sys.path:
    sys.path.insert(0, str(ROOT))


def main() -> int:
    from locust import HttpUser

    from scenarios.async_soak import AsyncSoakUser
    from scenarios.form_submission import FormSubmissionUser
    from scenarios.sync_chain import SyncChainUser

    for cls in (SyncChainUser, FormSubmissionUser, AsyncSoakUser):
        if not issubclass(cls, HttpUser):
            print(f"fail: {cls} is not HttpUser", file=sys.stderr)
            return 1
    print("ok: three scenario user classes registered")
    return 0


if __name__ == "__main__":
    sys.exit(main())
