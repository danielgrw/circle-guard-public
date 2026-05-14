"""Async chain soak: sparse survey posts + occasional promotion stats read (Kafka path)."""

from __future__ import annotations

import random
from datetime import datetime, timezone

from locust import HttpUser, between, task

from utils.config import derive_service_origin, http_timeout_seconds
from utils.jwt_helper import LocustAuth


_SYMPTOM_POOL = ("fever", "cough", "fatigue")


class AsyncSoakUser(HttpUser):
    weight = 1
    wait_time = between(5, 15)

    def on_start(self) -> None:
        self.auth_origin = derive_service_origin("circleguard-auth-service")
        self.form_origin = derive_service_origin("circleguard-form-service")
        self.promo_origin = derive_service_origin("circleguard-promotion-service")
        self.auth = LocustAuth(self.client)
        self.auth.login(self.auth_origin)

    @task(3)
    def submit_survey_sparse(self) -> None:
        if not self.auth.token:
            self.auth.login(self.auth_origin)
        if not self.auth.token:
            return
        symptoms = random.sample(_SYMPTOM_POOL, k=random.randint(1, 2))
        self.client.post(
            f"{self.form_origin}/api/v1/surveys",
            headers=self.auth.bearer_headers(),
            json={
                "symptoms": list(symptoms),
                "temperature": round(random.uniform(36.0, 38.0), 1),
                "contactWithInfected": False,
                "timestamp": datetime.now(timezone.utc).isoformat(),
            },
            name="/api/v1/surveys (soak)",
            timeout=http_timeout_seconds(),
        )

    @task(1)
    def promotion_health_stats(self) -> None:
        self.client.get(
            f"{self.promo_origin}/api/v1/health-status/stats",
            name="/api/v1/health-status/stats",
            timeout=http_timeout_seconds(),
        )
