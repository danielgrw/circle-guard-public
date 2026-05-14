import { test, expect } from '@playwright/test';
import {
  deriveServiceOrigin,
  getQrToken,
  loginAndGetToken,
} from './utils';

test('promotion assesses risk and gateway reflects it', async ({ request }) => {
  const formOrigin = deriveServiceOrigin('circleguard-form-service');
  const gateOrigin = deriveServiceOrigin('circleguard-gateway-service');

  const token = await loginAndGetToken(request);

  await request.post(`${formOrigin}/api/v1/surveys`, {
    headers: { Authorization: `Bearer ${token}` },
    data: {
      symptoms: ['fever', 'cough', 'loss_of_taste'],
      temperature: 39.0,
      contactWithInfected: true,
      timestamp: new Date().toISOString(),
    },
  });

  await expect
    .poll(
      async () => {
        const qrToken = await getQrToken(request, token);
        const validateRes = await request.post(`${gateOrigin}/api/v1/gate/validate`, {
          data: { token: qrToken },
        });
        if (!validateRes.ok()) return false;
        const validateData = (await validateRes.json()) as {
          valid?: boolean;
          status?: string;
        };
        return (
          validateData.valid === false ||
          (typeof validateData.status === 'string' && validateData.status !== 'GREEN')
        );
      },
      { timeout: 30_000 }
    )
    .toBe(true);
});
