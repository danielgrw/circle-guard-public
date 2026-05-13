"""JWT acquisition via auth-service REST — same contract as E2E (login + optional QR)."""

from __future__ import annotations

import os
from typing import Any

from utils.config import http_timeout_seconds


class LocustAuth:
    """Per-user token cache for Locust HttpUser.client."""

    def __init__(self, client: Any) -> None:
        self.client = client
        self.token: str | None = None
        self.anonymous_id: str | None = None

    def login(self, auth_origin: str) -> None:
        username = os.environ.get("LOCUST_AUTH_USERNAME", "testuser")
        password = os.environ.get("LOCUST_AUTH_PASSWORD", "password")
        resp = self.client.post(
            f"{auth_origin}/api/v1/auth/login",
            json={"username": username, "password": password},
            name="/api/v1/auth/login",
            timeout=http_timeout_seconds(),
        )
        if resp.status_code >= 400:
            return
        try:
            body = resp.json()
        except ValueError:
            return
        tok = body.get("token")
        aid = body.get("anonymousId")
        if isinstance(tok, str) and tok and isinstance(aid, str) and aid:
            self.token = tok
            self.anonymous_id = aid

    def bearer_headers(self) -> dict[str, str]:
        if not self.token:
            return {}
        return {"Authorization": f"Bearer {self.token}"}

    def fetch_qr_token(self, auth_origin: str) -> str | None:
        if not self.token:
            return None
        resp = self.client.get(
            f"{auth_origin}/api/v1/auth/qr/generate",
            headers=self.bearer_headers(),
            name="/api/v1/auth/qr/generate",
            timeout=http_timeout_seconds(),
        )
        if resp.status_code >= 400:
            return None
        try:
            body = resp.json()
        except ValueError:
            return None
        qr = body.get("qrToken")
        if isinstance(qr, str) and qr:
            return qr
        return None
