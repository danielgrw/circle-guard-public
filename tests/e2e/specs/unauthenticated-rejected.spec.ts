import { test, expect } from '@playwright/test';
import { deriveServiceOrigin } from './utils';

test('gateway returns a 401 response when unauthenticated', async ({ request }) => {
  const gateOrigin = deriveServiceOrigin('circleguard-gateway-service');
  const res = await request.get(`${gateOrigin}/api/v1/gate/validate`);
  expect(res.status()).toBe(401);
});
