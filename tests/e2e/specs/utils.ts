import { APIRequestContext, expect } from '@playwright/test';

type ServiceDnsName =
  | 'circleguard-auth-service'
  | 'circleguard-gateway-service'
  | 'circleguard-form-service'
  | 'circleguard-promotion-service';

/** Port/host mapping lives here so spec files stay free of literals (Story 3.2 AC2). */
const SERVICE_PORT: Record<ServiceDnsName, number> = {
  'circleguard-auth-service': 8180,
  'circleguard-gateway-service': 8087,
  'circleguard-form-service': 8086,
  'circleguard-promotion-service': 8088,
};

const ORIGIN_ENV: Record<ServiceDnsName, string | undefined> = {
  'circleguard-auth-service':
    process.env.AUTH_SERVICE_BASE_URL ?? process.env.CIRCLEGUARD_AUTH_ORIGIN,
  'circleguard-gateway-service':
    process.env.GATEWAY_SERVICE_BASE_URL ?? process.env.CIRCLEGUARD_GATEWAY_ORIGIN,
  'circleguard-form-service':
    process.env.FORM_SERVICE_BASE_URL ?? process.env.CIRCLEGUARD_FORM_ORIGIN,
  'circleguard-promotion-service':
    process.env.PROMOTION_SERVICE_BASE_URL ?? process.env.CIRCLEGUARD_PROMOTION_ORIGIN,
};

/** Derive per-service origins from {@code STAGE_BASE_URL}/{@code DEV_BASE_URL}. */
export function deriveServiceOrigin(service: ServiceDnsName): string {
  const direct = ORIGIN_ENV[service];
  if (direct?.trim()) {
    return direct.replace(/\/+$/, '');
  }

  const base = process.env.STAGE_BASE_URL || process.env.DEV_BASE_URL || 'http://localhost';
  const defaultPort = SERVICE_PORT[service];

  if (base.includes(service)) {
    return base.replace(/\/+$/, '');
  }

  try {
    const url = new URL(base);
    if (process.env.CI && url.hostname !== 'localhost') {
      return `http://${service}-stage:${defaultPort}`;
    }
    url.port = String(defaultPort);
    return url.toString().replace(/\/+$/, '');
  } catch {
    return `${base.replace(/\/+$/, '')}:${defaultPort}`;
  }
}

/** Subject (`sub`) claim from JWT payload segment — auth→JWT chain (AC3). */
export function jwtSubjectFromToken(token: string): string {
  const segments = token.split('.');
  if (segments.length < 2) {
    throw new Error('Malformed JWT — expected at least header and payload segments.');
  }
  const payloadSegment = segments[1];
  const normalized = payloadSegment.replace(/-/g, '+').replace(/_/g, '/');
  const pad = '='.repeat((4 - (normalized.length % 4)) % 4);

  try {
    const json = Buffer.from(normalized + pad, 'base64').toString('utf8');
    const parsed = JSON.parse(json) as { sub?: string };
    if (!parsed.sub) {
      throw new Error('JWT payload missing sub claim.');
    }
    return parsed.sub;
  } catch (e) {
    if (e instanceof Error && e.message.startsWith('JWT')) {
      throw e;
    }
    const detail = e instanceof Error ? e.message : String(e);
    throw new Error(`JWT payload decode failed: ${detail}`);
  }
}

export async function loginSession(
  request: APIRequestContext,
  username = 'testuser',
  password = 'password'
): Promise<{ token: string; anonymousId: string }> {
  const authOrigin = deriveServiceOrigin('circleguard-auth-service');
  const loginRes = await request.post(`${authOrigin}/api/v1/auth/login`, {
    data: { username, password },
  });
  expect(loginRes.ok()).toBeTruthy();
  const body = (await loginRes.json()) as { token?: string; anonymousId?: string };
  if (!body.token?.length || !body.anonymousId?.length) {
    throw new Error('Login response missing token or anonymousId');
  }
  return { token: body.token, anonymousId: body.anonymousId };
}

export async function loginAndGetToken(
  request: APIRequestContext,
  username?: string,
  password?: string
): Promise<string> {
  const { token } = await loginSession(request, username, password);
  return token;
}

export async function getQrToken(request: APIRequestContext, jwtToken: string): Promise<string> {
  const authOrigin = deriveServiceOrigin('circleguard-auth-service');
  const qrRes = await request.get(`${authOrigin}/api/v1/auth/qr/generate`, {
    headers: { Authorization: `Bearer ${jwtToken}` },
  });
  expect(qrRes.ok()).toBeTruthy();
  const raw = (await qrRes.json()) as Record<string, unknown>;
  const qr = raw.qrToken;
  if (typeof qr !== 'string' || qr.length === 0) {
    throw new Error('QR generate response missing non-empty qrToken string');
  }
  return qr;
}

export async function fetchSuspectCount(request: APIRequestContext): Promise<number> {
  const promoOrigin = deriveServiceOrigin('circleguard-promotion-service');
  const res = await request.get(`${promoOrigin}/api/v1/health-status/stats`);
  expect(res.ok()).toBeTruthy();
  const body = (await res.json()) as { suspectCount?: number };
  return typeof body.suspectCount === 'number' ? body.suspectCount : 0;
}
