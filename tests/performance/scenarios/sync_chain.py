"""Sync chain load: login → QR → gateway gate validate (mirrors E2E auth-flow)."""

from __future__ import annotations

from locust import HttpUser, between, task

from utils.config import derive_service_origin, http_timeout_seconds
from utils.jwt_helper import LocustAuth


class SyncChainUser(HttpUser):
    """Exercises gateway `/api/v1/gate/validate` with a QR token from auth."""

    weight = 2
    wait_time = between(1, 3)

    def on_start(self) -> None:
        self.auth_origin = derive_service_origin("circleguard-auth-service")
        self.gate_origin = derive_service_origin("circleguard-gateway-service")
        self.auth = LocustAuth(self.client)
        self.auth.login(self.auth_origin)
        self.qr_token = (
            self.auth.fetch_qr_token(self.auth_origin) if self.auth.token else None
        )

    @task
    def validate_gate(self) -> None:
        if not self.qr_token:
            self.auth.login(self.auth_origin)
            self.qr_token = self.auth.fetch_qr_token(self.auth_origin)
        if not self.qr_token:
            return
        self.client.post(
            f"{self.gate_origin}/api/v1/gate/validate",
            json={"token": self.qr_token},
            name="/api/v1/gate/validate",
            timeout=http_timeout_seconds(),
        )
