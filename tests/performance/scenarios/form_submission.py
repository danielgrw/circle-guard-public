"""Form submission flood — POST /api/v1/surveys (mirrors E2E form-submission)."""

from __future__ import annotations

import random
from datetime import datetime, timezone

from locust import HttpUser, between, task

from utils.config import derive_service_origin, http_timeout_seconds
from utils.jwt_helper import LocustAuth


_SYMPTOM_POOL = ("fever", "cough", "fatigue", "headache", "nausea")


class FormSubmissionUser(HttpUser):
    weight = 5
    wait_time = between(0.3, 1.5)

    def on_start(self) -> None:
        self.auth_origin = derive_service_origin("circleguard-auth-service")
        self.form_origin = derive_service_origin("circleguard-form-service")
        self.auth = LocustAuth(self.client)
        self.auth.login(self.auth_origin)

    @task
    def submit_health_survey(self) -> None:
        if not self.auth.token:
            self.auth.login(self.auth_origin)
        if not self.auth.token:
            return
        symptoms = random.sample(_SYMPTOM_POOL, k=random.randint(1, 3))
        self.client.post(
            f"{self.form_origin}/api/v1/surveys",
            headers=self.auth.bearer_headers(),
            json={
                "symptoms": list(symptoms),
                "temperature": round(random.uniform(36.0, 39.5), 1),
                "contactWithInfected": random.choice((True, False)),
                "timestamp": datetime.now(timezone.utc).isoformat(),
            },
            name="/api/v1/surveys",
            timeout=http_timeout_seconds(),
        )
