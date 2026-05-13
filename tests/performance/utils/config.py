"""Resolve service base URLs — mirrors tests/e2e/specs/utils.ts `deriveServiceOrigin`."""

from __future__ import annotations

import os
from urllib.parse import urlparse

SERVICE_PORT: dict[str, int] = {
    "circleguard-auth-service": 8180,
    "circleguard-gateway-service": 8087,
    "circleguard-form-service": 8086,
    "circleguard-promotion-service": 8088,
}

ORIGIN_ENV: dict[str, str | None] = {
    "circleguard-auth-service": os.environ.get("AUTH_SERVICE_BASE_URL")
    or os.environ.get("CIRCLEGUARD_AUTH_ORIGIN"),
    "circleguard-gateway-service": os.environ.get("GATEWAY_SERVICE_BASE_URL")
    or os.environ.get("CIRCLEGUARD_GATEWAY_ORIGIN"),
    "circleguard-form-service": os.environ.get("FORM_SERVICE_BASE_URL")
    or os.environ.get("CIRCLEGUARD_FORM_ORIGIN"),
    "circleguard-promotion-service": os.environ.get("PROMOTION_SERVICE_BASE_URL")
    or os.environ.get("CIRCLEGUARD_PROMOTION_ORIGIN"),
}


def derive_service_origin(service: str) -> str:
    direct = ORIGIN_ENV.get(service)
    if direct and str(direct).strip():
        return str(direct).strip().rstrip("/")

    base = (
        os.environ.get("STAGE_BASE_URL")
        or os.environ.get("DEV_BASE_URL")
        or "http://localhost"
    )
    default_port = SERVICE_PORT[service]

    if service in base:
        return base.rstrip("/")

    normalized = base if "://" in base else f"http://{base}"
    parsed = urlparse(normalized)
    hostname = parsed.hostname or "localhost"
    scheme = parsed.scheme or "http"

    if os.environ.get("CI") and hostname != "localhost":
        return f"http://{service}-stage:{default_port}"

    return f"{scheme}://{hostname}:{default_port}".rstrip("/")


def http_timeout_seconds() -> float:
    """Single timeout (connect + read combined) passed to Requests from Locust scenarios."""
    raw = os.environ.get("LOCUST_HTTP_TIMEOUT_SECONDS", "60")
    try:
        return max(1.0, float(raw))
    except ValueError:
        return 60.0
