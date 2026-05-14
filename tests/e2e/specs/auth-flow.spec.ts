import { test, expect } from '@playwright/test';
import {
  deriveServiceOrigin,
  getQrToken,
  jwtSubjectFromToken,
  loginSession,
} from './utils';

test('validates the full gateway→auth→identity JWT issuance chain end-to-end', async ({
  request,
}) => {
  const gateOrigin = deriveServiceOrigin('circleguard-gateway-service');

  const { token, anonymousId } = await loginSession(request);
  expect(jwtSubjectFromToken(token)).toBe(anonymousId);

  const qrToken = await getQrToken(request, token);

  const validateRes = await request.post(`${gateOrigin}/api/v1/gate/validate`, {
    data: { token: qrToken },
  });
  expect(validateRes.ok()).toBeTruthy();
  const validateData = await validateRes.json();
  expect(validateData.valid).toBeDefined();
});
